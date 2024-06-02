package com.researchspace.api.v1.controller;

import static com.researchspace.session.UserSessionTracker.USERS_KEY;

import com.researchspace.api.v1.FormsApi;
import com.researchspace.api.v1.controller.FormTemplatesCommon.FormFieldPost;
import com.researchspace.api.v1.controller.FormTemplatesCommon.FormPost;
import com.researchspace.api.v1.controller.FormTemplatesCommon.LinkTemplateProvider;
import com.researchspace.api.v1.model.ApiForm;
import com.researchspace.api.v1.model.ApiFormInfo;
import com.researchspace.api.v1.model.ApiFormSearchResult;
import com.researchspace.api.v1.service.RSFormApiHandler;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.dtos.FormSharingCommand;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.record.AbstractForm;
import com.researchspace.model.record.FormType;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.views.FormSearchCriteria;
import com.researchspace.service.FormManager;
import com.researchspace.service.IconImageManager;
import com.researchspace.session.UserSessionTracker;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/** Only active in 'run' mode to facilitate API tests */
@ApiController
public class FormsApiController extends BaseApiController implements FormsApi {

  private static final String PUBLISH_LINK = FORMS_ENDPOINT + "/{id}/" + "publish";
  private static final String UNPUBLISH_LINK = FORMS_ENDPOINT + "/{id}/" + "unpublish";
  private static final String SHARE_LINK = FORMS_ENDPOINT + "/{id}/" + "share";
  private static final String UNSHARE_LINK = FORMS_ENDPOINT + "/{id}/" + "unshare";
  private static final String ICON_LINK = FORMS_ENDPOINT + "/{id}/icon/{iconId}";

  private LinkTemplateProvider linkTemplatesMap;

  @PostConstruct
  public void init() {
    linkTemplatesMap = createFormLinkTemplateMap();
  }

  // package scoped for testing
  LinkTemplateProvider createFormLinkTemplateMap() {
    return LinkTemplateProvider.builder()
        .publish(PUBLISH_LINK)
        .unpublish(UNPUBLISH_LINK)
        .share(SHARE_LINK)
        .unshare(UNSHARE_LINK)
        .icon(ICON_LINK)
        .self(FORMS_ENDPOINT)
        .apiBaseUrl(() -> getApiBaseURI())
        .build();
  }

  private @Autowired FormManager formMgr;
  private @Autowired RSFormApiHandler formApiHandler;

  private @Autowired IconImageManager iconImageManager;
  private @Autowired FormTemplatesCommon formTemplateCommon;

  @Override
  public ApiFormSearchResult getForms(
      @Valid DocumentApiPaginationCriteria pgCrit,
      @Valid ApiFormSearchConfig apiSrchConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    PaginationCriteria<RSForm> internalPgCrit =
        getPaginationCriteriaForApiSearch(pgCrit, RSForm.class);
    FormSearchCriteria srchConfig = configureFormSearch(apiSrchConfig);
    ISearchResults<RSForm> internalForms = formMgr.searchForms(user, srchConfig, internalPgCrit);
    ApiFormSearchResult apiFormsSearchResults = new ApiFormSearchResult();
    List<ApiFormInfo> formInfoList = new ArrayList<>();
    convertISearchResults(
        pgCrit,
        apiSrchConfig,
        user,
        internalForms,
        apiFormsSearchResults,
        formInfoList,
        form -> new ApiFormInfo(form),
        form -> buildAndAddSelfLink(FORMS_ENDPOINT, form));
    return apiFormsSearchResults;
  }

  private FormSearchCriteria configureFormSearch(ApiFormSearchConfig apiSrchConfig) {
    FormSearchCriteria srchConfig = new FormSearchCriteria();
    srchConfig.setIncludeSystemForm(true);
    srchConfig.setPublishedOnly(true);
    srchConfig.setInUserMenu(false);
    srchConfig.setSearchTerm(apiSrchConfig.getQuery());
    srchConfig.setFormType(FormType.NORMAL);
    // set userforms-only forms unless explicitly set to see all
    srchConfig.setUserFormsOnly("all".equals(apiSrchConfig.getScope()) ? false : true);
    return srchConfig;
  }

  @Override
  public <T extends FieldForm> ApiForm editForm(
      @PathVariable Long id,
      @RequestBody @Valid FormPost formPost,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    throwBindExceptionIfErrors(errors);
    UserSessionTracker activeUsers = (UserSessionTracker) servletContext.getAttribute(USERS_KEY);
    AbstractForm updatedForm = formApiHandler.editForm(id, formPost, user, activeUsers);
    return convertToApiForm(user, updatedForm);
  }

  @Override
  public <T extends FieldForm> ApiForm createForm(
      @RequestBody @Valid FormPost formPost,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    throwBindExceptionIfErrors(errors);
    RSForm form = formMgr.create(user);
    form.setName(formPost.getName());
    form.setTags(formPost.getTags());
    form.setFormType(formPost.getFormType());
    form = formMgr.save(form, user);
    for (FormFieldPost<? extends FieldForm> toPost : formPost.getFields()) {
      formMgr.createFieldForm(toPost, form.getId(), user);
    }
    form = formMgr.getWithPopulatedFieldForms(form.getId(), user);

    return convertToApiForm(user, form);
  }

  @Override
  public ApiForm getFormById(@PathVariable Long id, @RequestAttribute(name = "user") User user)
      throws BindException {
    Optional<FormType> type = formMgr.getTypeById(id);
    if (!type.isPresent()) {
      throw new NotFoundException(createNotFoundMessage("SampleTemplate or form", id));
    }
    log.info("type is %s");
    try {
      AbstractForm value = formMgr.getWithPopulatedFieldForms(id, user);
      return convertToApiForm(user, value);

    } catch (DataAccessException e) {
      throw new NotFoundException(createNotFoundMessage("SampleTemplate", id));
    }
  }

  @Override
  public ApiForm publish(@PathVariable Long id, @RequestAttribute(name = "user") User user)
      throws BindException {
    RSForm form = formMgr.publish(id, true, null, user);
    return convertToApiForm(user, form);
  }

  @Override
  public ApiForm unpublish(@PathVariable Long id, @RequestAttribute(name = "user") User user)
      throws BindException {
    RSForm form = formMgr.publish(id, false, null, user);
    return convertToApiForm(user, form);
  }

  @Override
  public ApiForm share(@PathVariable Long id, @RequestAttribute(name = "user") User user)
      throws BindException {
    FormSharingCommand shareingCommand = new FormSharingCommand();
    shareingCommand.setGroupOptions(Arrays.asList(new String[] {"READ"}));
    RSForm form = formMgr.updatePermissions(id, shareingCommand, user);
    return convertToApiForm(user, form);
  }

  @Override
  public ApiForm globalShare(@PathVariable Long id, @RequestAttribute(name = "user") User user)
      throws BindException {
    boolean isSysadmin = false;
    for (Role role : user.getRoles()) {
      if (Role.SYSTEM_ROLE.equals(role)) {
        isSysadmin = true;
      }
    }
    if (!isSysadmin) {
      throw new AuthorizationException("Only sysadmin can use this API");
    }
    FormSharingCommand shareingCommand = new FormSharingCommand();
    shareingCommand.setGroupOptions(Arrays.asList(new String[] {"READ"}));
    shareingCommand.setWorldOptions(Arrays.asList(new String[] {"READ"}));
    RSForm form = formMgr.updatePermissions(id, shareingCommand, user);
    return convertToApiForm(user, form);
  }

  @Override
  public ApiForm unshare(@PathVariable Long id, @RequestAttribute(name = "user") User user)
      throws BindException {
    FormSharingCommand shareingCommand = new FormSharingCommand();
    shareingCommand.setGroupOptions(Arrays.asList(new String[] {"NONE"}));
    RSForm form = formMgr.updatePermissions(id, shareingCommand, user);
    return convertToApiForm(user, form);
  }

  @Override
  public ApiForm setIcon(
      @PathVariable Long id,
      @RequestParam("file") MultipartFile file,
      @RequestAttribute(name = "user") User user)
      throws IOException {
    AbstractForm form = formApiHandler.saveImage(file, id, user);
    return convertToApiForm(user, form);
  }

  @Override
  public void getIcon(
      @PathVariable Long formId,
      @PathVariable Long iconId,
      @RequestAttribute(name = "user") User user,
      HttpServletResponse response)
      throws IOException {
    iconImageManager.getIconEntity(iconId, response.getOutputStream(), this::getDefaultFormIcon);
  }

  private byte[] getDefaultFormIcon() {
    return getIconImageBytes("text.png");
  }

  public void deleteForm(@PathVariable Long id, @RequestAttribute(name = "user") User user) {
    formMgr
        .delete(id, user)
        .orElseThrow(() -> new NotFoundException(createNotFoundMessage("Form", id)));
  }

  private ApiForm convertToApiForm(User user, AbstractForm form) {
    ApiForm apiForm = new ApiForm(form);
    formTemplateCommon.addLinks(user, form, apiForm, linkTemplatesMap);
    return apiForm;
  }
}
