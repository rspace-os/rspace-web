package com.researchspace.api.v1.controller;

import static com.researchspace.service.FolderManager.API_INBOX_LOCK;
import static java.lang.String.format;

import com.axiope.search.SearchManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.DocumentsApi;
import com.researchspace.api.v1.model.ApiDocument;
import com.researchspace.api.v1.model.ApiDocumentField;
import com.researchspace.api.v1.model.ApiDocumentInfo;
import com.researchspace.api.v1.model.ApiDocumentSearchResult;
import com.researchspace.api.v1.model.ApiSearchQuery;
import com.researchspace.api.v1.model.ApiSearchQuery.OperatorEnum;
import com.researchspace.api.v1.model.ApiSearchTerm;
import com.researchspace.api.v1.model.ApiSearchTerm.QueryTypeEnum;
import com.researchspace.api.v1.service.ApiFieldsHelper;
import com.researchspace.api.v1.service.RecordApiManager;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.model.EditStatus;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.WorkspaceFilters;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.dtos.WorkspaceSettings;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.DocumentFieldAttachmentInitializationPolicy;
import com.researchspace.model.record.DocumentFieldInitializationPolicy;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.FormType;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.CompositeRecordOperationResult;
import com.researchspace.model.views.ServiceOperationResultCollection;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.service.FolderManager;
import com.researchspace.service.FormManager;
import com.researchspace.service.RecordDeletionManager;
import com.researchspace.service.impl.RecordDeletionManagerImpl.DeletionSettings;
import com.researchspace.session.UserSessionTracker;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;

@ApiController
public class DocumentsApiController extends BaseApiController implements DocumentsApi {

  @Autowired private FolderManager folderMgr;
  @Autowired private SearchManager searchManager;
  @Autowired private FormManager formMgr;
  @Autowired private RecordApiManager recordApiMgr;
  @Autowired private ApiFieldsHelper apiFieldsHelper;
  @Autowired private RecordDeletionManager deletionMgr;
  @Autowired private BaseRecordManager baseRecordMgr;

  @Override
  public ApiDocumentSearchResult getDocuments(
      @Valid DocumentApiPaginationCriteria pgCrit,
      @Valid ApiDocSearchConfig srchConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    log.info("Incoming pagination is {}", pgCrit.toString());
    throwBindExceptionIfErrors(errors);

    // parse the search queries
    boolean queryProvided = !StringUtils.isEmpty(srchConfig.getQuery());
    boolean advancedQueryProvided = !StringUtils.isEmpty(srchConfig.getAdvancedQuery());
    if (queryProvided && advancedQueryProvided) {
      throw new IllegalArgumentException(
          "please provide either a query or advancedQuery, not both");
    }

    ApiSearchQuery searchQuery = new ApiSearchQuery();
    if (queryProvided) {
      searchQuery.addTermsItem(new ApiSearchTerm(srchConfig.getQuery(), QueryTypeEnum.GLOBAL));
    }
    if (advancedQueryProvided) {
      try {
        searchQuery =
            new ObjectMapper().readValue(srchConfig.getAdvancedQuery(), ApiSearchQuery.class);
      } catch (IOException e) {
        throw new IllegalArgumentException(
            "problem with parsing advancedQuery: " + srchConfig.getAdvancedQuery());
      }
    }

    PaginationCriteria<BaseRecord> internalPgCrit =
        getPaginationCriteriaForApiSearch(pgCrit, BaseRecord.class);
    WorkspaceFilters filters = new WorkspaceFilters();
    // this ensures we only get documents, not folders or notebooks
    filters.setDocumentsFilter(true);

    String filter = srchConfig.getFilter();
    if ("favorites".equalsIgnoreCase(filter)) {
      filters.setFavoritesFilter(true);
    } else if ("sharedWithMe".equalsIgnoreCase(filter)) {
      filters.setSharedFilter(true);
    } else {
      filters.setViewableItemsFilter(true);
    }

    /*
     * Actual search
     */
    ISearchResults<BaseRecord> docSearchResults;
    if (searchQuery.hasAnySearchTerms()) {

      WorkspaceListingConfig config =
          convertToWorkspaceListingConfig(searchQuery, internalPgCrit, filters, user);
      try {
        docSearchResults = searchManager.searchWorkspaceRecords(config, user);
      } catch (IOException e) {
        throw new AuthorizationException(
            "Authorisation problem with searching by query: ["
                + srchConfig.getAdvancedQuery()
                + "]");
      }

    } else {
      // if no search terms were provided we list all records
      docSearchResults = recordManager.getFilteredRecords(filters, internalPgCrit, user);
    }

    /*
     * Converting search results to ApiSearchResult
     */
    ApiDocumentSearchResult searchResult = new ApiDocumentSearchResult();
    List<ApiDocumentInfo> info = new ArrayList<ApiDocumentInfo>();
    convertISearchResults(
        pgCrit,
        srchConfig,
        user,
        docSearchResults,
        searchResult,
        info,
        baseRecord -> new ApiDocumentInfo(baseRecord.asStrucDoc(), user),
        apiInfo -> buildAndAddSelfLink(DOCUMENTS_ENDPOINT, apiInfo));
    return searchResult;
  }

  private WorkspaceListingConfig convertToWorkspaceListingConfig(
      ApiSearchQuery searchQuery,
      PaginationCriteria<BaseRecord> pgCrit,
      WorkspaceFilters filters,
      User user) {

    Long fId = folderMgr.getRootFolderForUser(user).getId();

    List<String> options = new ArrayList<>();
    List<String> terms = new ArrayList<>();
    for (ApiSearchTerm term : searchQuery.getTerms()) {

      term.updateTermToISO8601IfDateTerm();

      options.add(term.getQueryType().toString());
      terms.add(term.getQuery());
    }

    boolean isAdvancedSearch = searchQuery.getTerms().size() > 1;
    String operator = searchQuery.getOperator().equals(OperatorEnum.OR) ? "OR" : "AND";

    WorkspaceListingConfig config =
        new WorkspaceListingConfig(
            pgCrit,
            options.toArray(new String[0]),
            terms.toArray(new String[0]),
            fId,
            isAdvancedSearch,
            operator,
            filters,
            WorkspaceSettings.WorkspaceViewMode.LIST_VIEW);
    return config;
  }

  @Override
  public ApiDocument getDocumentById(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {

    StructuredDocument doc = retrieveDocumentForApiUser(id, user);
    ApiDocument apiDocument = new ApiDocument(doc, user);
    apiFieldsHelper.updateOutgoingApiFieldsContent(apiDocument.getFields(), user);

    buildAndAddSelfLink(DOCUMENTS_ENDPOINT, apiDocument);
    buildAndAddSelfLink(FORMS_ENDPOINT, apiDocument.getForm());
    addFileLinks(apiDocument);

    return apiDocument;
  }

  /**
   * retrieves structured document with given id, or throws exception if doesn't exist / user have
   * no permissions
   */
  private StructuredDocument retrieveDocumentForApiUser(Long id, User user) {
    StructuredDocument doc = null;
    boolean exists = recordManager.exists(id);

    // we don't want to give away information about whether resource actually exists or not.
    // So catch AuthorizationException and rethrow.
    if (exists) {
      try {
        BaseRecord baseRecord =
            recordManager.getRecordWithLazyLoadedProperties(
                id,
                user,
                new DocumentFieldInitializationPolicy(
                    new DocumentFieldAttachmentInitializationPolicy()),
                false);
        EditStatus res = recordManager.requestRecordView(id, user, getCurrentActiveUsers());
        if (EditStatus.ACCESS_DENIED.equals(res)) {
          throw new NotFoundException(createNotFoundMessage(id));
        }

        if (baseRecord.isStructuredDocument()) {
          doc = (StructuredDocument) baseRecord;
        } else {
          // Document exists but it's not a structured document
          throw new NotFoundException(createNotFoundMessage(id));
        }
      } catch (AuthorizationException auth) {
        SECURITY_LOG.warn(
            "Unauthorised API call by user {} to access resource {}", user.getUsername(), id);
        throw new NotFoundException(createNotFoundMessage(id));
      }
    } else {
      throw new NotFoundException(createNotFoundMessage(id));
    }
    return doc;
  }

  private String createNotFoundMessage(Long id) {
    return createNotFoundMessage("Document", id);
  }

  /*
   * ==============================================
   *      document create and update methods
   * ==============================================
   */

  @Override
  public ApiDocument createNewDocument(
      @RequestBody @Valid ApiDocument apiDocument,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    throwBindExceptionIfErrors(errors);

    RSForm docForm = formMgr.retrieveFormForApiForm(user, apiDocument.getForm(), FormType.NORMAL);
    apiFieldsHelper.checkApiFieldsMatchingFormFields(
        apiDocument.getFields(), docForm.getFieldForms(), user);

    Long createdDocId = null;
    synchronized (API_INBOX_LOCK) {
      createdDocId = recordApiMgr.createNewDocument(apiDocument, docForm, user);
    }
    return getDocumentById(createdDocId, user);
  }

  @Override
  public ApiDocument createNewRevision(
      @PathVariable Long id,
      @RequestBody @Valid ApiDocument apiDocument,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException, DocumentAlreadyEditedException {

    throwBindExceptionIfErrors(errors);
    StructuredDocument doc = retrieveDocumentForApiUser(id, user);
    validateDocIsMutable(errors, doc);
    convertApiFieldsToMatchDocFields(apiDocument, doc.getFields());
    apiFieldsHelper.checkApiFieldsMatchingFormFields(
        apiDocument.getFields(), doc.getForm().getFieldForms(), user);

    recordApiMgr.createNewRevision(doc, apiDocument, user);
    return getDocumentById(id, user);
  }

  private void validateDocIsMutable(BindingResult errors, StructuredDocument doc)
      throws BindException {
    if (doc.isSigned()) {
      errors.addError(
          new ObjectError(
              "doc", String.format("Document %d is signed and  cannot be altered", doc.getId())));
    }
    throwBindExceptionIfErrors(errors);
  }

  /**
   * Modifies apiDocument.fields list so, if it's not empty, it matches in order and size the
   * docFields list. Each element of a new list contains either apiField with 'id' value matching
   * docField 'id' value for given index, or empty api field.
   *
   * @throws IllegalArgumentException if apiDocument.fields contains element with 'id' value that is
   *     not on docFields list.
   */
  protected void convertApiFieldsToMatchDocFields(ApiDocument apiDocument, List<Field> docFields) {
    List<ApiDocumentField> apiFields = apiDocument.getFields();
    if (apiFields == null || apiFields.size() == 0) {
      return; // if fields list empty then it can stay empty
    }

    if (apiFields.size() == docFields.size()) {
      /* if user provided the same number of fields, then ids are not required,
       * but if provided they must match docFields ids */
      for (int i = 0; i < apiFields.size(); i++) {
        Long apiFieldId = apiFields.get(i).getId();
        Long docFieldId = docFields.get(i).getId();
        if (apiFieldId != null && !apiFieldId.equals(docFieldId)) {
          throw new IllegalArgumentException(
              String.format(
                  "Provided field id: %d does not match document field id: %d",
                  apiFieldId, docFieldId));
        }
      }
      return; // provided fields list contains all fields so it can stay
    }

    /* only part of the fields provided - sort them according to doc fields,
     * and fill the blanks with empty apiFields */
    List<ApiDocumentField> convertedApiFields = new ArrayList<>();
    for (Field docField : docFields) {
      Long docFieldId = docField.getId();
      ApiDocumentField matchingApiField = null;
      for (ApiDocumentField apiField : apiFields) {
        if (apiField != null && docFieldId.equals(apiField.getId())) {
          matchingApiField = apiField;
          break;
        }
      }
      if (matchingApiField != null) {
        apiFields.remove(matchingApiField);
        convertedApiFields.add(matchingApiField);
      } else {
        convertedApiFields.add(new ApiDocumentField());
      }
    }

    // all provided apiFields should be matched at this point
    if (apiFields.size() > 0) {
      throw new IllegalArgumentException("Provided fields don't match document fields");
    }
    apiDocument.setFields(convertedApiFields);
  }

  @Override
  public void deleteDocumentById(
      @PathVariable Long id,
      @RequestAttribute(name = "user") User user,
      HttpServletResponse response)
      throws DocumentAlreadyEditedException {
    BaseRecord toDelete = null;
    try {
      toDelete = baseRecordMgr.get(id, user);
    } catch (DataAccessException e) {
      throw new NotFoundException(createNotFoundMessage("Document", id));
    }
    if (!toDelete.getOwner().equals(user)
        || !toDelete.isStructuredDocument()
        || toDelete.isDeleted()) {
      throw new NotFoundException(format(createNotFoundMessage("Document", id)));
    }
    Folder parent =
        toDelete
            .getOwnerParent()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Item to delete must be in user's folder tree, not a shared folder"));

    boolean isNotebookEntryDeletion = parent.isNotebook();
    UserSessionTracker users = getCurrentActiveUsers();
    DeletionSettings settings =
        DeletionSettings.builder()
            .currentUsers(users)
            .parent(parent)
            .notebookEntryDeletion(isNotebookEntryDeletion)
            .noAccessHandler((itemId, e) -> {})
            .build();

    ServiceOperationResultCollection<CompositeRecordOperationResult, Long> result =
        deletionMgr.doDeletion(
            new Long[] {id}, user::getUsername, settings, user, ProgressMonitor.NULL_MONITOR);

    // only 1 item deleted at a time, either it failed or succeeded
    if (result.isAllSucceeded()) {
      response.setStatus(HttpStatus.NO_CONTENT.value());
    } else {
      throw new RuntimeException(" Unexpected error deleting item " + id);
    }
  }
}
