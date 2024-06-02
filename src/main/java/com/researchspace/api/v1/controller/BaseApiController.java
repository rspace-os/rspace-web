package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiDocument;
import com.researchspace.api.v1.model.ApiDocumentField;
import com.researchspace.api.v1.model.ApiFile;
import com.researchspace.api.v1.model.ApiPaginatedResultList;
import com.researchspace.api.v1.model.IdentifiableObject;
import com.researchspace.api.v1.model.LinkableApiObject;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.IControllerInputValidator;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RecordManager;
import com.researchspace.session.UserSessionTracker;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.servlet.ServletContext;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.util.UriComponentsBuilder;

public class BaseApiController implements ServletContextAware {

  protected static final String API_V1 = "/api/v1";

  protected static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);
  protected final transient Logger log = LoggerFactory.getLogger(getClass());

  public static final String DOCUMENTS_ENDPOINT = "/documents";
  public static final String FOLDERS_ENDPOINT = "/folders";
  public static final String FILES_ENDPOINT = "/files";
  public static final String FOLDER_TREE_ENDPOINT = "/folders/tree";
  public static final String EVENTS_ENDPOINT = "/activity";

  public static final String FORMS_ENDPOINT = "/forms";
  public static final String SHARE_ENDPOINT = "/share";
  public static final String GROUPS_ENDPOINT = "/groups";
  public static final String EXPORT_INTERNAL_ENDPOINT = "/export";
  public static final String JOBS_INTERNAL_ENDPOINT = "/jobs";

  public static final String SYSADMIN_USERS_ENDPOINT = "/sysadmin/users";
  public static final MediaType CSV = new MediaType("text", "csv");

  protected @Autowired RecordManager recordManager;
  protected @Autowired IPropertyHolder properties;
  protected @Autowired IControllerInputValidator inputValidator;
  protected @Autowired MessageSourceUtils messages;

  void setMessageSource(MessageSourceUtils messages) {
    this.messages = messages;
  }

  protected ServletContext servletContext;

  public void setServletContext(ServletContext servletContext) {
    this.servletContext = servletContext;
  }

  protected ServletContext getServletContext() {
    return servletContext;
  }

  /** Gets tracker of currently logged in users */
  protected UserSessionTracker getCurrentActiveUsers() {
    return (UserSessionTracker) servletContext.getAttribute(UserSessionTracker.USERS_KEY);
  }

  protected String getServerURL() {
    return properties.getServerUrl();
  }

  protected UriComponentsBuilder getApiBaseURI() {
    return UriComponentsBuilder.fromHttpUrl(getServerURL()).path(API_V1);
  }

  protected <T> PaginationCriteria<T> getPaginationCriteriaForApiSearch(
      ApiPaginationCriteria apiPgCrit, Class<T> clazz) {
    PaginationCriteria<T> pgCrit = PaginationCriteria.createDefaultForClass(clazz);
    pgCrit.setPageNumber(apiPgCrit.getPageNumber().longValue());
    pgCrit.setResultsPerPage(apiPgCrit.getPageSize());
    if (apiPgCrit.getOrderBy() != null) {
      pgCrit.setOrderBy(apiPgCrit.getSort().getOrderBy());
      pgCrit.setSortOrder(apiPgCrit.getSort().getSortOrder());
    }
    return pgCrit;
  }

  protected String buildParameterisedLink(final String path, Map<String, ?> uriVariables) {
    return getApiBaseURI().path(path).buildAndExpand(uriVariables).encode().toUriString();
  }

  protected void buildAndAddSelfLink(final String endpoint, final IdentifiableObject info) {
    String path = endpoint + "/" + info.getId();
    String link = getApiBaseURI().path(path).build().encode().toUriString();
    info.addSelfLink(link);
  }

  protected void addFileLinks(ApiDocument apiDocument) {
    for (ApiDocumentField field : apiDocument.getFields()) {
      for (ApiFile file : field.getFiles()) {
        addFileLink(file);
      }
    }
  }

  protected void addFileLink(ApiFile file) {
    file.addSelfLink(buildFileLink(file, false));
    file.addEnclosureLink(buildFileLink(file, true));
  }

  private String buildFileLink(ApiFile file, boolean fileDataLink) {
    String path = FILES_ENDPOINT + "/" + file.getId() + (fileDataLink ? "/file" : "");
    return getApiBaseURI().path(path).build().encode().toUriString();
  }

  protected String getMessage(String key, Object[] args) {
    return messages.getMessage(key, args);
  }

  /**
   * Standard response for a resource that is not authorised, or does not exist.
   *
   * @param id
   * @return
   */
  protected String createNotFoundMessage(String resourceType, Long id) {
    return messages.getResourceNotFoundMessage(resourceType, id);
  }

  /**
   * Generic converter of an {@link ISearchResults} to a listing of API entities. Generic params are
   * :
   *
   * <ul>
   *   <li>&lt;T&gt; the type of the internal ISearchResult
   *   <li>&lt;R&gt; the type of the Api entity being queried or listed
   * </ul>
   *
   * @param pgCrit
   * @param srchConfig
   * @param user
   * @param internalSearchResults
   * @param apiSearchResult
   * @param apiItemList
   * @param internalModelToApiModel A Function that converts an internal type T to an Api type R.
   * @param linkBuilder Uses information in R to set links
   */
  protected <T, R extends LinkableApiObject> void convertISearchResults(
      ApiPaginationCriteria pgCrit,
      ApiSearchConfig srchConfig,
      User user,
      ISearchResults<T> internalSearchResults,
      ApiPaginatedResultList<R> apiSearchResult,
      List<R> apiItemList,
      Function<T, R> internalModelToApiModel,
      Consumer<R> linkBuilder) {

    if (internalSearchResults != null) {
      apiSearchResult.setTotalHits(internalSearchResults.getTotalHits());
      apiSearchResult.setPageNumber(internalSearchResults.getPageNumber());
      for (T internalSrchResult : internalSearchResults.getResults()) {
        R apiInfo = internalModelToApiModel.apply(internalSrchResult);
        linkBuilder.accept(apiInfo);
        apiItemList.add(apiInfo);
      }
    }
    apiSearchResult.setItems(apiItemList);
    apiSearchResult.addNavigationLinks(getApiBaseURI(), pgCrit, srchConfig);
  }

  void throwBindExceptionIfErrors(BindingResult errors) throws BindException {
    if (errors.hasErrors()) {
      throw new BindException(errors);
    }
  }

  protected byte[] getIconImageBytes(String iconFileName) {
    InputStream in = servletContext.getResourceAsStream("/images/icons/" + iconFileName);
    byte[] data;
    try {
      data = IOUtils.toByteArray(in);
    } catch (IOException e) {
      return new byte[0];
    }
    return data;
  }
}
