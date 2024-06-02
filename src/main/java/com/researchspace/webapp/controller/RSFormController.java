package com.researchspace.webapp.controller;

import static com.researchspace.session.UserSessionTracker.USERS_KEY;

import com.researchspace.api.v1.service.RSFormApiHandler;
import com.researchspace.core.util.DefaultURLPaginator;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.PaginationObject;
import com.researchspace.core.util.PaginationUtil;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.EditStatus;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.SignatureStatus;
import com.researchspace.model.User;
import com.researchspace.model.dtos.ChoiceFieldDTO;
import com.researchspace.model.dtos.ChoiceFieldDTOValidator;
import com.researchspace.model.dtos.DateFieldDTO;
import com.researchspace.model.dtos.DateFieldDTOValidator;
import com.researchspace.model.dtos.FormMenu;
import com.researchspace.model.dtos.FormSharingCommand;
import com.researchspace.model.dtos.NumberFieldDTO;
import com.researchspace.model.dtos.NumberFieldDTOValidator;
import com.researchspace.model.dtos.RadioFieldDTO;
import com.researchspace.model.dtos.RadioFieldDTOValidator;
import com.researchspace.model.dtos.StringFieldDTO;
import com.researchspace.model.dtos.StringFieldDTOValidator;
import com.researchspace.model.dtos.TextFieldDTO;
import com.researchspace.model.dtos.TextFieldValidator;
import com.researchspace.model.dtos.TimeFieldDTO;
import com.researchspace.model.dtos.TimeFieldDTOValidator;
import com.researchspace.model.field.ChoiceFieldForm;
import com.researchspace.model.field.DateFieldForm;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.field.NumberFieldForm;
import com.researchspace.model.field.RadioFieldForm;
import com.researchspace.model.field.StringFieldForm;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.field.TimeFieldForm;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.AbstractForm;
import com.researchspace.model.record.CopyIndependentFormAndFieldFormPolicy;
import com.researchspace.model.record.DetailedRecordInformation;
import com.researchspace.model.record.FormOperation;
import com.researchspace.model.record.FormState;
import com.researchspace.model.record.IconEntity;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.views.FormSearchCriteria;
import com.researchspace.service.FormManager;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.session.UserSessionTracker;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

/** Handler for the editing, creation, listing, sharing of RSForms in RS. */
@Controller
@RequestMapping("/workspace/editor/form")
@SessionAttributes("formSharingCommand")
public class RSFormController extends BaseController {

  private static final String ANY_FIELD_NAME = "Field";

  private @Autowired FormManager formManager;
  private @Autowired RSFormApiHandler formHandler;
  @Autowired private SystemPropertyPermissionManager systemPropertyPermissionManager;

  @PostMapping("/")
  // permissions checking handled in service methos
  public ModelAndView createForm(Model model) throws IOException {
    final int height = 32;
    final int width = 32;

    User subject = userManager.getAuthenticatedUserInSession();
    RSForm form = formManager.create(subject);
    form.setEditStatus(EditStatus.EDIT_MODE);

    IconEntity icon = new IconEntity();
    InputStream in = servletContext.getResourceAsStream("/images/icons/doc.png");
    if (in != null) {
      icon.setIconImage(IOUtils.toByteArray(in));
      icon.setImgName("doc.png");
      icon.setHeight(height);
      icon.setWidth(width);
      icon.setParentId(form.getId());
    }
    form.setIconId(icon.getId());
    model.addAttribute("template", form);
    model.addAttribute("fieldKeys", SDocHelper.popoulateFieldTypeList());
    model.addAttribute("editStatus", form.getEditStatus());
    model.addAttribute("templateOperation", FormOperation.CREATE);
    model.addAttribute(
        "publish_allowed",
        systemPropertyPermissionManager.isPropertyAllowed(subject, "public_sharing"));
    return new ModelAndView("workspace/editor/form");
  }

  /**
   * @param formId
   * @return
   */
  @PostMapping("/unlockform")
  public AjaxReturnObject<Boolean> unlockForm(
      @RequestParam("id") long formId, Principal principal) {

    User user = userManager.getUserByUsername(principal.getName());
    formManager.unlockForm(formId, user);
    return new AjaxReturnObject<Boolean>(true, null);
  }

  /**
   * REnames a form
   *
   * @param formId
   * @param newname
   * @param user
   * @return String 'success'
   * @throws AuthorizationException
   */
  @ResponseBody
  @PostMapping("/ajax/rename")
  public AjaxReturnObject<String> rename(
      @RequestParam("recordId") long formId,
      @RequestParam("newName") String newname,
      Principal user) {
    User u = userManager.getUserByUsername(user.getName());
    RSForm toSave = formManager.get(formId, u);
    if (!permissionUtils.isPermitted(toSave, PermissionType.WRITE, u)) {
      throw new AuthorizationException(authGenerator.getFailedMessage(u, "rename form"));
    }
    toSave.setName(newname);
    formManager.save(toSave, u);

    return new AjaxReturnObject<String>("Success", null);
  }

  @ResponseBody
  @PostMapping("/ajax/description")
  public AjaxReturnObject<String> setDocumentDescription(
      @RequestParam("recordId") long recordId,
      @RequestParam("newName") String desc,
      Principal user) {
    User u = userManager.getUserByUsername(user.getName());
    RSForm toSave = formManager.get(recordId, u);
    if (!permissionUtils.isPermitted(toSave, PermissionType.WRITE, u)) {
      throw new AuthorizationException(authGenerator.getFailedMessage(u, "Set form description"));
    }
    toSave.setDescription(desc);
    formManager.save(toSave, u);
    return new AjaxReturnObject<String>("Success", null);
  }

  @ResponseBody
  @PostMapping("ajax/createDateField")
  // permissions handled in manager
  public AjaxReturnObject<DateFieldForm> createDateField(
      DateFieldDTO<DateFieldForm> dto, @RequestParam("recordId") long formId) {
    User subject = userManager.getAuthenticatedUserInSession();
    ErrorList el = inputValidator.validateAndGetErrorList(dto, new DateFieldDTOValidator());
    if (el != null) {
      return new AjaxReturnObject<DateFieldForm>(null, el);
    }
    DateFieldForm dft = formManager.createFieldForm(dto, formId, subject);
    dft.setForm(null);
    return new AjaxReturnObject<DateFieldForm>(dft, null);
  }

  @ResponseBody
  @PostMapping("ajax/createStringField")
  // permissions handled in manager
  public AjaxReturnObject<StringFieldForm> createStringField(
      @RequestParam("defaultValue") String defaultValue,
      @RequestParam("ifPassword") String isPassword,
      @RequestParam("name") String fieldName,
      @RequestParam("recordId") long formId,
      @RequestParam("mandatory") boolean isMandatory) {

    User subject = userManager.getAuthenticatedUserInSession();
    StringFieldDTO<StringFieldForm> dto =
        new StringFieldDTO<StringFieldForm>(fieldName, isMandatory, isPassword, defaultValue);
    ErrorList eo = inputValidator.validateAndGetErrorList(dto, new StringFieldDTOValidator());
    if (eo != null) {
      return new AjaxReturnObject<StringFieldForm>(null, eo);
    }

    StringFieldForm stringField = formManager.createFieldForm(dto, formId, subject);
    stringField.setForm(null);

    return new AjaxReturnObject<StringFieldForm>(stringField, null);
  }

  /**
   * Publishes/unpublishes without any notion of access control
   *
   * @param id
   * @param toPublish
   * @return
   */
  @ResponseBody
  @PostMapping("ajax/publish")
  public AjaxReturnObject<FormState> setPublishedStatus(
      @RequestParam("templateId") Long id,
      @RequestParam("publish") boolean toPublish,
      Principal p) {
    User u = userManager.getUserByUsername(p.getName());
    RSForm t = formManager.publish(id, toPublish, null, u);
    return new AjaxReturnObject<FormState>(t.getPublishingState(), null);
  }

  @GetMapping("/ajax/publishAndShare")
  public String publishAndShare(
      Model model, Principal subject, @RequestParam("templateId") Long templateId) {
    RSForm form = formManager.get(templateId, userManager.getUserByUsername(subject.getName()));
    FormSharingCommand formShareComndc = new FormSharingCommand(form);
    model.addAttribute(formShareComndc);
    model.addAttribute("groupAvailableOptionList", formShareComndc.getGroupAvailableOptions());
    model.addAttribute("worldAvaialbleOptionList", formShareComndc.getWorldAvailableOptions());
    return "workspace/editor/formSharingConfig";
  }

  @ResponseBody
  @PostMapping("/ajax/publishAndShare")
  // auth handled in publish() method
  public AjaxReturnObject<FormState> publishAndSharePost(
      @ModelAttribute FormSharingCommand tsc, Principal user) {
    User u = userManager.getUserByUsername(user.getName());
    RSForm form = formManager.publish(tsc.getFormId(), true, tsc, u);
    return new AjaxReturnObject<FormState>(form.getPublishingState(), null);
  }

  @ResponseBody
  @PostMapping("/ajax/updateSharePermissions")
  public AjaxReturnObject<FormState> updateSharePermissions(
      @ModelAttribute FormSharingCommand tsc, Principal user) {
    User u = userManager.getUserByUsername(user.getName());
    RSForm form = formManager.updatePermissions(tsc.getFormId(), tsc, u);
    return new AjaxReturnObject<FormState>(form.getPublishingState(), null);
  }

  @ResponseBody
  @PostMapping("ajax/saveEditedStringField")
  public AjaxReturnObject<StringFieldForm> saveEditedStringField(
      @RequestParam("defaultValue") String defaultValue,
      @RequestParam("ifPassword") String isPassword,
      @RequestParam("name") String fieldName,
      @RequestParam("fieldId") long fieldId,
      @RequestParam("mandatory") boolean isMandatory) {

    User subject = userManager.getAuthenticatedUserInSession();
    StringFieldDTO<StringFieldForm> dto =
        new StringFieldDTO<StringFieldForm>(fieldName, isMandatory, isPassword, defaultValue);
    ErrorList eo = inputValidator.validateAndGetErrorList(dto, new StringFieldDTOValidator());
    if (eo != null) {
      return new AjaxReturnObject<StringFieldForm>(null, eo);
    }
    StringFieldForm updated = formManager.updateFieldForm(dto, fieldId, subject);
    updated.setForm(null);
    return new AjaxReturnObject<StringFieldForm>(updated, null);
  }

  @ResponseBody
  @PostMapping("ajax/saveEditedTextField")
  public AjaxReturnObject<TextFieldForm> saveEditedTextField(
      TextFieldDTO<TextFieldForm> dto, @RequestParam("fieldId") long fieldId) {
    User subject = userManager.getAuthenticatedUserInSession();
    ErrorList errors = inputValidator.validateAndGetErrorList(dto, new TextFieldValidator());
    if (errors != null) {
      return new AjaxReturnObject<TextFieldForm>(null, errors);
    }
    TextFieldForm updated = formManager.updateFieldForm(dto, fieldId, subject);
    updated.setForm(null);
    return new AjaxReturnObject<TextFieldForm>(updated, null);
  }

  @ResponseBody
  @PostMapping("ajax/saveEditedDateField")
  public AjaxReturnObject<DateFieldForm> saveEditedDateField(
      DateFieldDTO<DateFieldForm> dto, @RequestParam("fieldId") long fieldId) {
    User subject = userManager.getAuthenticatedUserInSession();
    ErrorList el = inputValidator.validateAndGetErrorList(dto, new DateFieldDTOValidator());
    if (el != null) {
      return new AjaxReturnObject<DateFieldForm>(null, el);
    }

    DateFieldForm dft = formManager.updateFieldForm(dto, fieldId, subject);
    dft.setForm(null);
    return new AjaxReturnObject<DateFieldForm>(dft, null);
  }

  @GetMapping("ajax/getFieldById")
  public ModelAndView viewFieldById(@RequestParam("fieldId") long fieldId, Model model) {
    FieldForm field = formManager.getField(fieldId);
    model.addAttribute("fieldTemplate", field);
    return new ModelAndView("workspace/editor/editform_fieldforms");
  }

  /**
   * Deletes a field from a form - TO BE USED WITH CAUTION as will lead to data integrity problems
   * with users' data
   *
   * @param fieldId
   * @return
   */
  @ResponseBody
  @PostMapping("ajax/deleteField")
  public AjaxReturnObject<String> deleteFieldbyId(@RequestParam("fieldId") long fieldId) {
    User subject = userManager.getAuthenticatedUserInSession();
    formManager.deleteFieldFromForm(fieldId, subject);
    return new AjaxReturnObject<String>("Success", null);
  }

  /**
   * Deletes a form - TO BE USED WITH CAUTION
   *
   * @param formIds
   * @return
   */
  @ResponseBody
  @PostMapping("ajax/deleteForm")
  public AjaxReturnObject<String> deleteFormbyId(
      @RequestParam("templateId[]") Long[] formIds, Principal user) {
    try {
      User subject = userManager.getUserByUsername(user.getName());
      for (Long id : formIds) {
        formManager.delete(id, subject);
        log.info("Deleted form id {}", id);
      }

    } catch (Exception e) {
      ErrorList el = new ErrorList();
      el.addErrorMsg("Exception deleting form. This has been logged.");
      return new AjaxReturnObject<String>(null, el);
    }
    return new AjaxReturnObject<String>("Success", null);
  }

  /**
   * Saves a form and updates its modification details.
   *
   * @param formId
   * @return An {@link AjaxReturnObject} with the template's ID
   */
  @ResponseBody
  @PostMapping("ajax/updateForm")
  public AjaxReturnObject<Long> updateForm(
      @RequestParam("templateId") long formId, Principal user) {

    User u = userManager.getUserByUsername(user.getName());
    formManager.updateVersion(formId, u);
    return new AjaxReturnObject<Long>(formId, null);
  }

  /**
   * Abandons updates to a form .
   *
   * @param formId
   * @return An {@link AjaxReturnObject} with the form's ID
   */
  @ResponseBody
  @PostMapping("ajax/abandonUpdateForm")
  public AjaxReturnObject<Long> abandonUpdateForm(
      @RequestParam("templateId") long formId, Principal user) {

    User u = userManager.getUserByUsername(user.getName());
    formManager.abandonUpdateForm(formId, u);
    return new AjaxReturnObject<Long>(formId, null);
  }

  @GetMapping("edit/{formid}")
  public ModelAndView editForm(Model model, Principal user, @PathVariable("formid") long formId)
      throws RecordAccessDeniedException {

    UserSessionTracker activeUsers = (UserSessionTracker) servletContext.getAttribute(USERS_KEY);
    User u = userManager.getUserByUsername(user.getName());
    RSForm form = formManager.getForEditing(formId, u, activeUsers);

    // rspac-2427.
    if (EditStatus.ACCESS_DENIED.equals(form.getEditStatus())) {
      throw new RecordAccessDeniedException(getResourceNotFoundMessage("Form", formId));
    }

    // we have at least read permission
    populateModelForEditing(model, form);
    return new ModelAndView("workspace/editor/form");
  }

  private void populateModelForEditing(Model model, RSForm form) {
    model.addAttribute("template", form);
    model.addAttribute("fieldKeys", SDocHelper.popoulateFieldTypeList());
    model.addAttribute("editStatus", form.getEditStatus());
    model.addAttribute("templateOperation", FormOperation.EDIT);
  }

  @GetMapping("list")
  public ModelAndView listFormsPage(
      Model model, PaginationCriteria<RSForm> pgCrit, FormSearchCriteria formSearchCrit) {
    User subject = userManager.getAuthenticatedUserInSession();
    updateModelWithForms(model, pgCrit, formSearchCrit);
    model.addAttribute(
        "publish_allowed",
        systemPropertyPermissionManager.isPropertyAllowed(subject, "public_sharing"));
    return new ModelAndView("workspace/editor/formList");
  }

  // /ajax/template/list?nameLike=xxx&pageNumber=xxx&resultsPerPage=xxx&sortOrder=ASC&orderBy=fieldName
  @GetMapping("ajax/list")
  public ModelAndView listForms(
      Model model, PaginationCriteria<RSForm> pgCrit, FormSearchCriteria formSearchCrit) {
    updateModelWithForms(model, pgCrit, formSearchCrit);
    return new ModelAndView("workspace/editor/formList_ajax");
  }

  @GetMapping("/ajax/search")
  public ModelAndView searchForms(
      Model model, PaginationCriteria<RSForm> pgCrit, FormSearchCriteria formSearchCrit) {
    updateModelWithForms(model, pgCrit, formSearchCrit);
    return new ModelAndView("workspace/editor/formList_ajax");
  }

  private void updateModelWithForms(
      Model model, PaginationCriteria<RSForm> pgCrit, FormSearchCriteria formSearchCrit) {

    User user = getUserFromSession();
    updateResultsPerPageProperty(user, pgCrit, Preference.FORM_RESULTS_PER_PAGE);
    ISearchResults<RSForm> forms =
        doPaginatedFormSearch(
            pgCrit,
            user,
            formSearchCrit.getSearchTerm(),
            false,
            false,
            false,
            formSearchCrit.isUserFormsOnly());
    List<PaginationObject> formListPages =
        PaginationUtil.generatePagination(
            forms.getTotalPages(),
            pgCrit.getPageNumber().intValue(),
            new FormPaginatedURLGenerator(null, pgCrit));

    model.addAttribute("paginationList", formListPages);
    model.addAttribute("templates", forms.getResults());
    model.addAttribute("rootId", folderManager.getRootFolderForUser(user).getId());
    model.addAttribute("numberRecords", forms.getHitsPerPage());

    Map<Long, Map<String, Boolean>> permsDTO = new HashMap<>();
    for (RSForm form : forms.getResults()) {
      Map<String, Boolean> perm = new HashMap<>();
      if (form.isNewState() && permissionUtils.isPermitted(form, PermissionType.DELETE, user)) {
        perm.put(PermissionType.DELETE.name(), true);
      } else {
        perm.put(PermissionType.DELETE.name(), false);
      }
      perm.put(
          PermissionType.SHARE.name(),
          permissionUtils.isPermitted(form, PermissionType.SHARE, user));
      permsDTO.put(form.getId(), perm);
    }
    model.addAttribute("formPermissions", permsDTO);
  }

  @ResponseBody
  @PostMapping("ajax/menutoggle")
  public AjaxReturnObject<Boolean> toggleMenu(
      @RequestParam("menu") Boolean menu, @RequestParam("formId") Long formId) {
    User subject = userManager.getAuthenticatedUserInSession();
    RSForm form = formManager.get(formId, subject);
    if (!permissionUtils.isPermitted(form, PermissionType.READ, subject)) {
      throw new AuthorizationException(
          String.format(
              "Unauthorised attempt to add form to menu[%d] by user %s",
              formId, subject.getUsername()));
    }
    if (menu) {
      formManager.addFormToUserCreateMenu(subject, formId, subject);
      return new AjaxReturnObject<Boolean>(true, null);
    } else {
      boolean deleted = formManager.removeFormFromUserCreateMenu(subject, formId, subject);
      return new AjaxReturnObject<Boolean>(deleted, null);
    }
  }

  @GetMapping("ajax/generateFormCreateMenu")
  public ModelAndView generateFormMenu(
      @RequestParam Long parentFolderId, Model model, Principal p) {
    User user = userManager.getUserByUsername(p.getName());
    FormMenu formMenu = formManager.generateFormMenu(user);
    model.addAttribute("forms", formMenu.getForms());
    model.addAttribute("parentFolderId", parentFolderId);
    model.addAttribute("formsForCreateMenuPagination", formMenu.getFormsForCreateMenuPagination());
    return new ModelAndView("workspace/editor/formListForCreateMenu");
  }

  /**
   * GEts the paginated list of forms for create menu by ajax. It is used for pagination links on
   * 'Create -> Other Document' dialog
   *
   * @param model
   * @param pgCrit - parsed from GET URL
   * @param parentFolderId
   * @return
   */
  @GetMapping("ajax/listForCreateMenu")
  public ModelAndView listFormsForCreateMenu(
      Model model, PaginationCriteria<RSForm> pgCrit, @RequestParam Long parentFolderId) {
    User user = getUserFromSession();

    ISearchResults<RSForm> forms =
        doPaginatedFormSearch(pgCrit, user, null, true, true, true, false);
    List<PaginationObject> formListPages =
        PaginationUtil.generatePagination(
            forms.getTotalPages(),
            pgCrit.getPageNumber().intValue(),
            new DefaultURLPaginator("ajax/listForCreateMenu", pgCrit),
            "form-create-menu-page-link");
    model.addAttribute("paginationList", formListPages);
    model.addAttribute("parentFolderId", parentFolderId);
    model.addAttribute("forms", forms.getResults());
    return new ModelAndView("workspace/editor/formListForCreateMenu");
  }

  private ISearchResults<RSForm> doPaginatedFormSearch(
      PaginationCriteria<RSForm> pgCrit,
      User user,
      String searchTerm,
      boolean publishedOnly,
      boolean includeSystem,
      boolean isInCreateMenu,
      boolean isUserFormOnly) {
    pgCrit.setClazz(RSForm.class);
    pgCrit.setOrderByIfNull("name"); // set default if not set

    FormSearchCriteria formSrchCrit = new FormSearchCriteria();
    formSrchCrit.setRequestedAction(PermissionType.READ);
    formSrchCrit.setIncludeSystemForm(includeSystem);
    formSrchCrit.setPublishedOnly(publishedOnly);
    formSrchCrit.setInUserMenu(isInCreateMenu);
    formSrchCrit.setUserFormsOnly(isUserFormOnly);

    if (!StringUtils.isEmpty(searchTerm)) {
      formSrchCrit.setSearchTerm(searchTerm);
    }

    ISearchResults<RSForm> forms = formManager.searchForms(user, formSrchCrit, pgCrit);
    return forms;
  }

  /**
   * copies a form
   *
   * @param formIds a list of form ids to copy
   * @return
   */
  @PostMapping("/ajax/copyForm")
  public String copyForm(@RequestParam("templateId[]") Long[] formIds, Principal user) {

    User u = userManager.getUserByUsername(user.getName());
    for (Long formId : formIds) {
      formManager.copy(formId, u, new CopyIndependentFormAndFieldFormPolicy());
    }
    return "redirect:/workspace/editor/form/ajax/list";
  }

  private User getUserFromSession() {
    return (User) SecurityUtils.getSubject().getSession().getAttribute(SessionAttributeUtils.USER);
  }

  @GetMapping("ajax/getField")
  public ModelAndView view(
      Model model,
      @RequestParam("name") String fieldtype,
      @RequestParam("recordId") String recordID) {

    FieldType ft = FieldType.getFieldTypeForString(fieldtype);
    if (ft == null) {
      throw new IllegalArgumentException("Unknown field type [" + fieldtype + "]");
    }

    Object viewModel = null;
    if (ft.equals(FieldType.NUMBER)) {
      viewModel = new NumberFieldForm(ANY_FIELD_NAME);
    } else if (ft.equals(FieldType.STRING)) {
      viewModel = new StringFieldForm(ANY_FIELD_NAME);
    } else if (ft.equals(FieldType.TEXT)) {
      viewModel = new TextFieldForm(ANY_FIELD_NAME);
    } else if (ft.equals(FieldType.RADIO)) {
      viewModel = new RadioFieldForm(ANY_FIELD_NAME);
    } else if (ft.equals(FieldType.CHOICE)) {
      viewModel = new ChoiceFieldForm(ANY_FIELD_NAME);
    } else if (ft.equals(FieldType.DATE)) {
      viewModel = new DateFieldForm(ANY_FIELD_NAME);
    } else if (ft.equals(FieldType.TIME)) {
      viewModel = new TimeFieldForm(ANY_FIELD_NAME);
    }

    model.addAttribute("fieldTemplate", viewModel);
    return new ModelAndView("workspace/editor/editform_fieldforms");
  }

  @ResponseBody
  @PostMapping("ajax/createNumberField")
  public AjaxReturnObject<NumberFieldForm> createNumberField(
      @RequestBody NumberFieldDTO<NumberFieldForm> dto) {

    User subject = userManager.getAuthenticatedUserInSession();
    ErrorList eo = inputValidator.validateAndGetErrorList(dto, new NumberFieldDTOValidator());
    if (eo != null) {
      return new AjaxReturnObject<NumberFieldForm>(null, eo);
    }

    NumberFieldForm numFT =
        formManager.createFieldForm(dto, Long.parseLong(dto.getParentId()), subject);
    numFT.setForm(null);
    return new AjaxReturnObject<NumberFieldForm>(numFT, null);
  }

  /**
   * Called when an existing number field is edited
   *
   * @param dto
   * @return
   */
  @ResponseBody
  @PostMapping("ajax/saveEditedNumberField")
  public AjaxReturnObject<NumberFieldForm> saveEditedNumber(
      @RequestBody NumberFieldDTO<NumberFieldForm> dto) {

    User subject = userManager.getAuthenticatedUserInSession();
    ErrorList eo = inputValidator.validateAndGetErrorList(dto, new NumberFieldDTOValidator());
    if (eo != null) {
      return new AjaxReturnObject<>(null, eo);
    }

    NumberFieldForm nft =
        formManager.updateFieldForm(dto, Long.parseLong(dto.getParentId()), subject);
    nft.setForm(null);
    return new AjaxReturnObject<>(nft, null);
  }

  @ResponseBody
  @PostMapping("ajax/createTextField")
  public AjaxReturnObject<TextFieldForm> createTextField(
      TextFieldDTO<TextFieldForm> dto, @RequestParam("recordId") long formId) {

    User subject = userManager.getAuthenticatedUserInSession();
    ErrorList errors = inputValidator.validateAndGetErrorList(dto, new TextFieldValidator());
    if (errors != null) {
      return new AjaxReturnObject<TextFieldForm>(null, errors);
    }

    TextFieldForm textField = formManager.createFieldForm(dto, formId, subject);
    textField.setForm(null);
    return new AjaxReturnObject<TextFieldForm>(textField, null);
  }

  /**
   * Called when saving a newly created Time field
   *
   * @param dto TimeFieldDTO
   * @param formId
   * @return
   */
  @ResponseBody
  @PostMapping("ajax/createTimeField")
  public AjaxReturnObject<TimeFieldForm> createTimeField(
      TimeFieldDTO<TimeFieldForm> dto, @RequestParam("recordId") long formId) {
    User subject = userManager.getAuthenticatedUserInSession();
    ErrorList el = inputValidator.validateAndGetErrorList(dto, new TimeFieldDTOValidator());
    if (el != null) {
      return new AjaxReturnObject<TimeFieldForm>(null, el);
    }
    TimeFieldForm templateField = formManager.createFieldForm(dto, formId, subject);
    templateField.setForm(null);
    return new AjaxReturnObject<TimeFieldForm>(templateField, null);
  }

  @ResponseBody
  @PostMapping("ajax/saveEditedTimeField")
  public AjaxReturnObject<TimeFieldForm> saveEditedTimeField(
      TimeFieldDTO<TimeFieldForm> dto, @RequestParam("fieldId") long fieldId) {
    User subject = userManager.getAuthenticatedUserInSession();
    ErrorList el = inputValidator.validateAndGetErrorList(dto, new TimeFieldDTOValidator());
    if (el != null) {
      return new AjaxReturnObject<TimeFieldForm>(null, el);
    }

    TimeFieldForm temp = formManager.updateFieldForm(dto, fieldId, subject);
    temp.setForm(null);
    return new AjaxReturnObject<TimeFieldForm>(temp, null);
  }

  @ResponseBody
  @PostMapping("ajax/createChoiceField")
  public AjaxReturnObject<ChoiceFieldForm> createChoiceField(
      @RequestParam("choiceValues") String choiceValues,
      @RequestParam("multipleChoice") String multipleChoice,
      @RequestParam("selectedValues") String selectedValues,
      @RequestParam("name") String fieldName,
      @RequestParam("recordId") long formId,
      @RequestParam("mandatory") boolean isMandatory) {

    User subject = userManager.getAuthenticatedUserInSession();
    ChoiceFieldDTO<ChoiceFieldForm> dto =
        new ChoiceFieldDTO<>(choiceValues, multipleChoice, selectedValues, fieldName, isMandatory);
    ErrorList el = inputValidator.validateAndGetErrorList(dto, new ChoiceFieldDTOValidator());
    if (el != null) {
      return new AjaxReturnObject<ChoiceFieldForm>(null, el);
    }

    ChoiceFieldForm cft = formManager.createFieldForm(dto, formId, subject);
    cft.setForm(null);
    return new AjaxReturnObject<ChoiceFieldForm>(cft, null);
  }

  @ResponseBody
  @PostMapping("ajax/saveEditedChoiceField")
  public AjaxReturnObject<ChoiceFieldForm> saveEditedChoiceField(
      @RequestParam("choiceValues") String choiceValues,
      @RequestParam("multipleChoice") String multipleChoice,
      @RequestParam("selectedValues") String selectedValues,
      @RequestParam("name") String fieldName,
      @RequestParam("fieldId") long fieldId,
      @RequestParam("mandatory") boolean isMandatory) {

    ChoiceFieldDTO<ChoiceFieldForm> dto =
        new ChoiceFieldDTO<>(choiceValues, multipleChoice, selectedValues, fieldName, isMandatory);
    ErrorList el = inputValidator.validateAndGetErrorList(dto, new ChoiceFieldDTOValidator());
    if (el != null) {
      return new AjaxReturnObject<ChoiceFieldForm>(null, el);
    }

    User subject = userManager.getAuthenticatedUserInSession();
    ChoiceFieldForm updated = formManager.updateFieldForm(dto, fieldId, subject);
    updated.setForm(null);
    return new AjaxReturnObject<ChoiceFieldForm>(updated, null);
  }

  @ResponseBody
  @PostMapping("ajax/saveEditedRadioField")
  public AjaxReturnObject<RadioFieldForm> saveEditedRadioField(
      @RequestParam("radioValues") String radioValues,
      @RequestParam("radioSelected") String radioSelected,
      @RequestParam("name") String fieldName,
      @RequestParam("fieldId") long fieldId,
      @RequestParam("showAsPickList") boolean showAsPickList,
      @RequestParam("sortAlphabetic") boolean sortAlphabetic,
      @RequestParam("mandatory") boolean isMandatory) {

    RadioFieldDTO<RadioFieldForm> dto =
        new RadioFieldDTO<>(
            radioValues, radioSelected, fieldName, showAsPickList, sortAlphabetic, isMandatory);
    ErrorList el = inputValidator.validateAndGetErrorList(dto, new RadioFieldDTOValidator());
    if (el != null) {
      return new AjaxReturnObject<RadioFieldForm>(null, el);
    }

    User subject = userManager.getAuthenticatedUserInSession();
    RadioFieldForm radioField = formManager.updateFieldForm(dto, fieldId, subject);
    radioField.setForm(null);
    return new AjaxReturnObject<RadioFieldForm>(radioField, null);
  }

  @ResponseBody
  @PostMapping("ajax/createRadioField")
  public AjaxReturnObject<RadioFieldForm> createRadioField(
      @RequestParam("radioValues") String radioValues,
      @RequestParam("radioSelected") String radioSelected,
      @RequestParam("name") String fieldName,
      @RequestParam("recordId") long formId,
      @RequestParam("showAsPickList") boolean showAsPickList,
      @RequestParam("sortAlphabetic") boolean sortAlphabetic,
      @RequestParam("mandatory") boolean isMandatory) {

    RadioFieldDTO<RadioFieldForm> dto =
        new RadioFieldDTO<>(
            radioValues, radioSelected, fieldName, showAsPickList, sortAlphabetic, isMandatory);
    ErrorList el = inputValidator.validateAndGetErrorList(dto, new RadioFieldDTOValidator());
    if (el != null) {
      return new AjaxReturnObject<RadioFieldForm>(null, el);
    }

    User subject = userManager.getAuthenticatedUserInSession();
    RadioFieldForm radioField = formManager.createFieldForm(dto, formId, subject);
    radioField.setForm(null);
    return new AjaxReturnObject<RadioFieldForm>(radioField, null);
  }

  /**
   * Saves a form and updates its modification details.
   *
   * @param formId
   * @return An {@link AjaxReturnObject} with the template's ID
   */
  @ResponseBody
  @PostMapping("ajax/saveForm")
  public AjaxReturnObject<Long> saveForm(
      @RequestParam("templateId") long formId, Principal subject) {

    log.info("Saving form with id {}", formId);
    User user = userManager.getUserByUsername(subject.getName());
    RSForm form = formManager.get(formId, user, true);
    formManager.save(form, user);

    return new AjaxReturnObject<Long>(formId, null);
  }

  @ResponseBody
  @PostMapping("/ajax/savetag")
  public AjaxReturnObject<Boolean> tagForm(
      @RequestParam Long recordId, @RequestParam String tagtext) {
    ErrorList el =
        inputValidator.validateAndGetErrorList(new RSpaceTag(tagtext), new TagValidator());
    if (el != null) {
      return new AjaxReturnObject<>(null, el);
    }
    formManager.saveFormTag(recordId, tagtext);
    return new AjaxReturnObject<>(true, null);
  }

  /**
   * Post handler for reordering fields during an edit session.
   *
   * @param formId
   * @param ids
   * @return {@link AjaxReturnObject} with boolean <code>true</code> on success
   */
  @ResponseBody
  @PostMapping("/reorderFields")
  public AjaxReturnObject<Boolean> reorderFields(
      @RequestParam Long formId, @RequestParam(value = "fieldids[]") Long[] ids) {

    List<Long> fieldIds = TransformerUtils.toList(ids);
    User subject = getUserFromSession();
    formManager.reorderFields(formId, fieldIds, subject);
    return new AjaxReturnObject<>(true, null);
  }

  /**
   * Saves an image icon for this form
   *
   * @param file
   * @param formId
   * @param p
   * @return
   * @throws IOException
   */
  @ResponseBody
  @PostMapping("/ajax/saveImage/{formId}")
  public AjaxReturnObject<Long> saveImage(
      @RequestParam("filex") MultipartFile file, @PathVariable("formId") Long formId, Principal p)
      throws IOException {

    User user = userManager.getUserByUsername(p.getName());
    AbstractForm form = formHandler.saveImage(file, formId, user);
    return new AjaxReturnObject<Long>(form.getIconId(), null);
  }

  @ResponseBody
  @GetMapping("/ajax/getFormInformation")
  public AjaxReturnObject<DetailedRecordInformation> getFormInfo(
      Principal principal, @RequestParam("templateId") Long templateId) {
    User user = userManager.getUserByUsername(principal.getName());
    RSForm form = formManager.get(templateId, user);

    // Check if the user has READ permissions for the form
    if (!permissionUtils.isPermitted(form, PermissionType.READ, user)) {
      return new AjaxReturnObject<DetailedRecordInformation>(
          null, ErrorList.of("Unauthorized attempt to get form info"));
    }

    DetailedRecordInformation info = new DetailedRecordInformation(form);
    info.setSignatureStatus(SignatureStatus.UNSIGNABLE);
    return new AjaxReturnObject<>(info, null);
  }
}
