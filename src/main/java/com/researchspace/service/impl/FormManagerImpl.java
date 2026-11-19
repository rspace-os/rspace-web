package com.researchspace.service.impl;

import com.researchspace.api.v1.model.ApiFormInfo;
import com.researchspace.core.util.DefaultURLPaginator;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.ListFormatUtils;
import com.researchspace.core.util.PaginationObject;
import com.researchspace.core.util.PaginationUtil;
import com.researchspace.core.util.SortOrder;
import com.researchspace.dao.FormCreateMenuDao;
import com.researchspace.dao.FormDao;
import com.researchspace.dao.FormUsageDao;
import com.researchspace.dao.IconImgDao;
import com.researchspace.model.AccessControl;
import com.researchspace.model.EditStatus;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.FormFieldSaveItem;
import com.researchspace.model.dtos.FormMenu;
import com.researchspace.model.dtos.FormSharingCommand;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.FormType;
import com.researchspace.model.record.FormUsage;
import com.researchspace.model.record.FormUserMenu;
import com.researchspace.model.record.IFormCopyPolicy;
import com.researchspace.model.record.IRecordFactory;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.TemporaryCopyLinkedToOriginalCopyPolicy;
import com.researchspace.model.views.FormSearchCriteria;
import com.researchspace.service.FormDefinitionCommand;
import com.researchspace.service.FormFieldsConflictException;
import com.researchspace.service.FormManager;
import com.researchspace.service.FormVersionUpdateException;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.session.UserSessionTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.stereotype.Service;

/**
 * This class does not perform validation of input values; this work is performed by the controller.
 */
@Service("formManager")
public class FormManagerImpl extends AbstractFormManagerImpl<RSForm> implements FormManager {
  private @Autowired FormDao formDao;
  private @Autowired FormUsageDao formUsageDao;
  private @Autowired FormCreateMenuDao formCreateMenudao;
  private @Autowired IconImgDao iconImgDao;
  private @Autowired IRecordFactory recordFactory;
  private @Autowired MessageSourceUtils messages;
  private @Autowired FormFieldListSaver fieldListSaver;

  public FormManagerImpl(@Autowired FormDao formDao) {
    super(formDao, RSForm.class);
    this.formDao = formDao;
    formTracker = new RecordEditorTracker();
  }

  @Override
  public RSForm get(Long id, User user) {
    return formDao.get(id, false);
  }

  public RSForm get(long id, User u, boolean includeDeleted) {
    return formDao.get(id, includeDeleted);
  }

  @Override
  public boolean formExistsInMenuForUser(String formStableID, User user) {
    return formCreateMenudao.formExistsInMenuForUser(formStableID, user);
  }

  /** Gets all non-temporary, current Forms */
  @Override
  public List<RSForm> getAllCurrentNormalForms() {
    return formDao.getAllCurrentNormalForms();
  }

  @Override
  public RSForm getBasicDocumentForm() {
    return formDao.getBasicDocumentForm();
  }

  @Override
  public Optional<RSForm> getCurrentSystemForm(String name) {
    return formDao.getCurrentSystemFormByName(name);
  }

  @Override
  public RSForm save(RSForm form, User user) {
    form = super.save(form, user);
    unlockUnlessHeldByToken(form, user);
    return form;
  }

  @Override
  public RSForm saveAndReleaseEditLock(RSForm form, String lockToken, User user) {
    if (StringUtils.isBlank(lockToken)) {
      return save(form, user);
    }
    form = super.save(form, user);
    releaseEditLock(lockToken, user);
    return form;
  }

  @RequiresPermissions("FORM:CREATE")
  @Override
  public RSForm create(User user) {
    RSForm form = recordFactory.createNewForm();
    doPopulateCoreFields(form, user);
    form = formDao.save(form);
    return form;
  }

  @RequiresPermissions("FORM:SHARE")
  @Override
  public RSForm updatePermissions(Long formid, FormSharingCommand config, User authUser) {
    RSForm form = formDao.get(formid);
    boolean altered = false;
    permissionUtils.assertIsPermitted(form, PermissionType.SHARE, authUser, "share form");
    // only save if config is different
    if (config != null && !form.getAccessControl().equals(config.toAccessControl())) {
      form.setAccessControl(config.toAccessControl());
      formDao.save(form);
    }
    return form;
  }

  @RequiresPermissions("FORM:SHARE")
  @Override
  public RSForm publish(Long id, boolean toPublish, FormSharingCommand config, User authUser) {
    RSForm form = formDao.get(id);
    if (form.isSystemForm() && !toPublish) {
      log.error("Attempted to unpublish a system form by user {}", authUser.getUsername());
      throw new UnsupportedOperationException(
          messages.getMessage("form.errors.systemFormUnpublish"));
    }
    boolean altered = false;
    if (!permissionUtils.isPermitted(form, PermissionType.SHARE, authUser)) {
      throw new AuthorizationException(
          messages.getMessage("form.errors.publishUnauthorized", new Object[] {form.getId()}));
    }
    // only update on status change
    if (toPublish && !form.isPublishedAndVisible()) {
      form.publish();
      altered = true;
    } else if (!toPublish && form.isPublishedAndVisible()) {
      form.unpublish();
      altered = true;
    }
    // only save if value has changed.
    if (altered) {
      if (config != null) {
        // update access control
        AccessControl ac = config.toAccessControl();
        form.setAccessControl(ac);
      }
      formDao.save(form);
    }
    return form;
  }

  @Override
  public Optional<RSForm> delete(long formId, User user) {
    return formDao.getSafeNull(formId).map(form -> attemptRemoveForm(formId, user, form));
  }

  private RSForm attemptRemoveForm(long formId, User user, RSForm form) {
    permissionUtils.assertIsPermitted(form, PermissionType.DELETE, user, "delete form");
    if (form.isNewState() || noDocsCreated(form)) {
      formDao.remove(formId);
    } else {
      throw new IllegalArgumentException(
          messages.getMessage(
              "form.errors.hasDocumentsCreated", new Object[] {form.getStableID()}));
    }
    return form;
  }

  private boolean noDocsCreated(RSForm form) {
    long docCount = formDao.countDocsCreatedFromForm(form);
    log.warn(
        "Form with stable ID {} has {} documents created from it", form.getStableID(), docCount);
    return docCount == 0;
  }

  @Override
  public List<RSForm> getDynamicMenuFormItems(User subject) {
    final int numDocuments = 100;
    final int limit = 4;
    Optional<FormUsage> lastUsed = formUsageDao.getMostRecentlyUsedFormForUser(subject);
    List<String> averaged = formUsageDao.getMostPopularForms(numDocuments, limit, null, subject);
    List<RSForm> rc = new ArrayList<>();

    for (String tempId : averaged) {
      RSForm toAdd = formDao.getMostRecentVersionForForm(tempId);
      if (toAdd != null) {
        rc.add(toAdd);
      }
    }
    final int averagedSize = averaged.size();
    if (lastUsed.isPresent()) {
      RSForm lastUsedTemp = formDao.getMostRecentVersionForForm(lastUsed.get().getFormStableID());
      if (lastUsedTemp == null) {
        log.warn(
            " Form with stableId {} does not have a "
                + "most recent published version. Has this form been unpublished?",
            lastUsed.get().getFormStableID());
      }
      // if last used is not in most used, put it at the end
      if (lastUsedTemp != null && !rc.contains(lastUsedTemp) && averagedSize > 0) {
        rc.set(averagedSize - 1, lastUsedTemp);
      }
    }
    Iterator<RSForm> it = rc.iterator();
    formCreateMenudao.updateFormsInMenuForUser(subject, rc);
    while (it.hasNext()) {
      if (!it.next().isInSubjectsMenu()) {
        it.remove();
      }
    }
    // So that the list always has at least one item
    if (rc.isEmpty()) {
      rc.add(getBasicDocumentForm());
    }
    return rc;
  }

  @RequiresPermissions("FORM:READ")
  // will shortcut DB access if no permission
  @Override
  public RSForm copy(Long formID, final User user, final IFormCopyPolicy<RSForm> policy) {
    log.info("Copying form with ID {}", formID);
    RSForm original = get(formID, user);
    RSForm copy = original.copy(policy);
    copy.setIconId(original.getIconId());
    if (!policy.isKeepOriginalOwnerInCopy()) {
      copy.setOwner(user);
    } else {
      copy.setOwner(original.getOwner());
    }
    formDao.save(copy);
    formDao.save(original);
    return copy;
  }

  @RequiresPermissions("FORM:READ")
  // minimal permission
  @Override
  public RSForm getForEditing(Long id, User user, UserSessionTracker activeUsers) {
    return getForEditing(id, user, activeUsers, true);
  }

  public RSForm getForEditing(
      Long id, User user, UserSessionTracker activeUsers, boolean useSessionToCalculateEditStatus) {
    return open(id, user, activeUsers, useSessionToCalculateEditStatus, false).form();
  }

  @RequiresPermissions("FORM:READ") // the minimal permission, as for getForEditing
  @Override
  public FormEditorPage getForEditorPage(Long id, User user, UserSessionTracker activeUsers) {
    Opened opened = open(id, user, activeUsers, true, true);
    return new FormEditorPage(opened.form(), opened.lockToken());
  }

  /** A form opened for editing, and the token of an editor page's hold on its lock, if any. */
  private record Opened(RSForm form, String lockToken) {}

  private Opened open(
      Long id,
      User user,
      UserSessionTracker activeUsers,
      boolean useSessionToCalculateEditStatus,
      boolean holdForEditorPage) {
    String lockToken = null;
    // if it's a new form, we'll return this - it's not been published yet
    RSForm formToEdit = get(id, user);

    if (hasWritePermission(user, formToEdit)) {
      // if we're starting to edit a new version
      if (!formToEdit.isNewState() && formToEdit.getTempForm() == null) {
        formToEdit = copy(id, user, new TemporaryCopyLinkedToOriginalCopyPolicy());
        // else we're resuming editing a temporary template
      } else if (!formToEdit.isNewState() && formToEdit.getTempForm() != null) {
        formToEdit = formToEdit.getTempForm();
      } else if (formToEdit.isTemporary()) {
        // this *is* a temp form so we'll just return it
        log.info("loading a temp form");
      }
      EditStatus es;
      if (holdForEditorPage) {
        // atomic with the hold, so a release by id cannot take the acquisition before it is held
        synchronized (editLockHolds) {
          es =
              formTracker.attemptToEdit(id, user, activeUsers, SessionAttributeUtils::getSessionId);
          if (EditStatus.EDIT_MODE.equals(es)) {
            lockToken = UUID.randomUUID().toString();
            editLockHolds.put(
                lockToken,
                new EditLockHold(id, user.getUsername(), SessionAttributeUtils.getSessionId()));
          }
        }
      } else {
        es =
            calculateEditStatus(id, user, activeUsers, useSessionToCalculateEditStatus, formToEdit);
      }
      formToEdit.setEditStatus(es);
    } else if (permissionUtils.isPermitted(formToEdit, PermissionType.READ, user)) {
      formToEdit.setEditStatus(EditStatus.CANNOT_EDIT_NO_PERMISSION);
    } else {
      formToEdit.setEditStatus(EditStatus.ACCESS_DENIED);
    }
    formToEdit.getFieldForms().size(); // initialize FTs from lazy-loading
    return new Opened(formToEdit, lockToken);
  }

  private EditStatus calculateEditStatus(
      Long id,
      User user,
      UserSessionTracker activeUsers,
      boolean useSessionToCalculateEditStatus,
      RSForm rc) {
    EditStatus es;
    if (useSessionToCalculateEditStatus) {
      es = formTracker.attemptToEdit(id, user, activeUsers, SessionAttributeUtils::getSessionId);
    } else {
      es =
          Boolean.TRUE.equals(formTracker.isSomeoneElseEditing(rc, user.getUsername()))
              ? EditStatus.CANNOT_EDIT_OTHER_EDITING
              : EditStatus.EDIT_MODE;
    }
    return es;
  }

  @Override
  public void unlockForm(RSForm form, User user) {
    unlockUnlessHeldByToken(form, user);
  }

  @Override
  public void unlockForm(Long formId, User user) {
    if (formDao.exists(formId)) {
      unlockUnlessHeldByToken(formDao.get(formId), user);
    }
  }

  /** A form editor page's hold on an edit lock, see {@link #getForEditorPage}. */
  private record EditLockHold(long formId, String username, String sessionId) {}

  /**
   * Holds by page token. A hold lasts as long as the acquisition it stands for: until its page
   * releases it, or until the session ends ({@link #removeFromEditorTracker}). Changes to holds and
   * to the acquisitions they stand for are made while synchronized on this map.
   */
  private final Map<String, EditLockHold> editLockHolds = new ConcurrentHashMap<>();

  /**
   * Releases one of the user's acquisitions on the form in this session by id, only if one is not
   * held by a form editor page's token, so a by-id release can never take a page's lock.
   */
  private void unlockUnlessHeldByToken(RSForm form, User user) {
    if (form == null || form.getId() == null || user == null) {
      return;
    }
    // outside a session (an archive import, a startup task) no lock can have been taken
    String sessionId = currentSessionId();
    if (sessionId == null) {
      return;
    }
    EditLockHold thisAcquisition = new EditLockHold(form.getId(), user.getUsername(), sessionId);
    synchronized (editLockHolds) {
      long heldByToken = editLockHolds.values().stream().filter(thisAcquisition::equals).count();
      if (formTracker.getEditCount(form.getId(), user.getUsername(), sessionId) > heldByToken) {
        formTracker.unlockRecord(form, user, () -> sessionId);
      }
    }
  }

  /** The current Shiro session's id, or null when there is no subject or session. */
  private static String currentSessionId() {
    Subject subject = ThreadContext.getSubject();
    Session session = subject == null ? null : subject.getSession(false);
    return session == null ? null : session.getId().toString();
  }

  @Override
  public boolean releaseEditLock(String token, User user) {
    String sessionId = currentSessionId();
    if (StringUtils.isBlank(token) || sessionId == null) {
      return false;
    }
    synchronized (editLockHolds) {
      EditLockHold hold = editLockHolds.get(token);
      if (hold == null
          || !hold.username().equals(user.getUsername())
          || !hold.sessionId().equals(sessionId)) {
        return false;
      }
      editLockHolds.remove(token);
      // by id, so a hold on a form deleted since (a reverted draft) is released too
      formTracker.unlockRecordById(hold.formId(), user, () -> sessionId);
    }
    return true;
  }

  @Override
  public Set<Long> getCopiedTemporaryFieldIds(RSForm form) {
    if (form == null || form.getId() == null || !form.isTemporary()) {
      return Collections.emptySet();
    }
    RSForm original = formDao.getOriginalFromTemporaryForm(form.getId());
    if (original == null) {
      return Collections.emptySet();
    }
    Set<Long> copiedFieldIds = new HashSet<>();
    for (FieldForm originalField : original.getFieldForms()) {
      if (originalField.getTempFieldForm() != null
          && originalField.getTempFieldForm().getId() != null) {
        copiedFieldIds.add(originalField.getTempFieldForm().getId());
      }
    }
    return copiedFieldIds;
  }

  @RequiresPermissions("FORM:READ")
  // minimal permission
  @Override
  public RSForm getForEditing(long id, User user, UserSessionTracker activeUsers, long iconId) {
    RSForm rc = getForEditing(id, user, activeUsers);
    iconImgDao.updateIconRelation(iconId, id);
    return rc;
  }

  public void abandonUpdateForm(long tempFormId, User user) {
    RSForm temp = get(tempFormId, user);
    RSForm orig = formDao.getOriginalFromTemporaryForm(tempFormId);
    for (FieldForm originals : orig.getFieldForms()) {
      originals.setTempFieldForm(null);
    }
    orig.setTempForm(null);
    formDao.removeFieldsFromForm(temp.getId());
    // they've all been deleted by previous method, so this object needs to be updated
    temp.setFieldForms(Collections.emptyList());
    formDao.save(temp);
    formDao.save(orig);
    formDao.remove(temp.getId());
  }

  @Override
  public RSForm updateVersion(Long tempFormId, User user) {
    RSForm temp = get(tempFormId, user);
    RSForm orig = formDao.getOriginalFromTemporaryForm(tempFormId);
    if (orig == null || orig.isSystemForm()) {
      log.error("Cannot update version of a system form!");
      return null;
    }
    List<FieldForm> fts = orig.getFieldForms();

    for (FieldForm origFT : fts) {
      origFT.setTempFieldForm(null);
    }

    // now make the temporary template the current one
    orig.setTempForm(null);
    temp.makeCurrentVersion(orig);
    // update the original
    formDao.save(orig);

    for (FieldForm ft : temp.getFieldForms()) {
      ft.setTemporary(false);
    }
    return formDao.save(temp);
  }

  @Override
  public ISearchResults<RSForm> searchForms(
      User user, FormSearchCriteria sc, PaginationCriteria<RSForm> pg) {
    ISearchResults<RSForm> results = formDao.getAllFormsByPermission(user, sc, pg);
    if (!results.getResults().isEmpty()) {
      formCreateMenudao.updateFormsInMenuForUser(user, results.getResults());
    }
    return results;
  }

  @Override
  public RSForm saveFormTag(Long formId, String tagtext) {
    RSForm form = formDao.get(formId);
    form.setTags(tagtext);
    return formDao.save(form);
  }

  @Override
  public void removeFromEditorTracker(String sessionId) {
    synchronized (editLockHolds) {
      formTracker.removeLockedRecordInSession(sessionId);
      editLockHolds.values().removeIf(hold -> hold.sessionId().equals(sessionId));
    }
  }

  @Override
  public FormUserMenu addFormToUserCreateMenu(User toAdd, Long formId, User subject) {
    log.info("Adding form {} to create menu of user {}", formId, toAdd.getUsername());
    RSForm form = formDao.get(formId);
    if (!form.isCurrent()) {
      throw new IllegalArgumentException(
          messages.getMessage(
              "form.errors.createMenuVersionMismatch",
              new Object[] {form.getVersion(), form.getId()}));
    }
    FormUserMenu inMenu = new FormUserMenu(toAdd, form);
    return formCreateMenudao.save(inMenu);
  }

  @Override
  public boolean removeFormFromUserCreateMenu(User user, Long formIdToRemove, User subject) {

    try {
      RSForm form = formDao.get(formIdToRemove);
      log.debug("Removing form {} from create menu of user {}", formIdToRemove, user.getUsername());
      boolean removed = formCreateMenudao.removeForUserAndForm(user, form.getStableID());
      if (removed) {
        log.info("form {} removed for user {}", formIdToRemove, user.getUsername());
      } else {
        log.warn("form {} NOT removed for user {}", formIdToRemove, user.getUsername());
      }
      return removed;
    } catch (DataAccessException e) {
      log.error(e.getMessage());
      return false;
    }
  }

  @Override
  public FormMenu generateFormMenu(User user) {
    // Creates form listing for create menu
    FormSearchCriteria sc = new FormSearchCriteria();
    sc.setRequestedAction(PermissionType.READ);
    sc.setIncludeSystemForm(true);
    sc.setPublishedOnly(true);
    sc.setInUserMenu(true);
    PaginationCriteria<RSForm> pgCritForm = PaginationCriteria.createDefaultForClass(RSForm.class);

    // Sort by name
    pgCritForm.setOrderByIfNull("name");
    pgCritForm.setSortOrder(SortOrder.ASC);
    ISearchResults<RSForm> forms = searchForms(user, sc, pgCritForm);
    List<PaginationObject> formsForCreateMenuPagination =
        PaginationUtil.generatePagination(
            forms.getTotalPages(),
            0,
            new DefaultURLPaginator("ajax/listForCreateMenu", pgCritForm),
            "form-create-menu-page-link");

    List<RSForm> menuToAdd = getDynamicMenuFormItems(user);
    return new FormMenu(forms.getResults(), menuToAdd, formsForCreateMenuPagination);
  }

  @Override
  public RSForm retrieveFormForApiForm(User user, ApiFormInfo apiForm, FormType formType) {
    RSForm form;
    Long apiFormId = apiForm == null ? null : apiForm.retrieveFormIdFromApiForm();
    if (apiFormId == null) {
      form = getBasicDocumentForm();
    } else {
      try {
        form = get(apiFormId, user);
      } catch (ObjectRetrievalFailureException e) {
        throw new IllegalArgumentException(
            messages.getMessage("form.errors.notFound", new Object[] {apiFormId}));
      }
    }
    return form;
  }

  @Override
  public RSForm findOldestFormByName(String formName) {
    return formDao.findOldestFormByName(formName);
  }

  @RequiresPermissions("FORM:WRITE")
  @Override
  public RSForm saveFields(Long formId, List<FormFieldSaveItem> fields, User user) {
    return doSaveFields(formDao.get(formId), fields, user, true);
  }

  @RequiresPermissions("FORM:WRITE")
  @Override
  public RSForm saveFields(
      Long formId, List<FormFieldSaveItem> fields, String expectedVersion, User user) {
    RSForm form = formDao.get(formId);
    if (!hasWritePermission(user, form)) {
      throw new AuthorizationException(messages.getMessage("form.errors.fieldUpdateUnauthorized"));
    }
    // another tab's save, committed while this one waited for the lock, would not be seen otherwise
    if (!formDao.lockUnchangedSinceTransactionStart(formId)
        || !fieldListSaver.version(form).equals(expectedVersion)) {
      throw new FormFieldsConflictException(
          messages.getMessage("form.errors.fieldsChangedElsewhere"));
    }
    return doSaveFields(form, fields, user, true);
  }

  @Override
  public String getFieldsVersion(RSForm form) {
    return fieldListSaver.version(form);
  }

  @Override
  public boolean canWrite(RSForm form, User user) {
    return hasWritePermission(user, form);
  }

  /**
   * The body of {@link #saveFields(Long, List, User)}, also used by the API entry points, which
   * call it directly and so carry their own permission annotations.
   *
   * @param protectCopiedFields whether omitting a field copied into a draft from the published
   *     version is an error (the form field editor) rather than a deletion (the forms API, which
   *     has always replaced the whole field list)
   */
  private RSForm doSaveFields(
      RSForm form, List<FormFieldSaveItem> fields, User user, boolean protectCopiedFields) {
    if (!hasWritePermission(user, form)) {
      throw new AuthorizationException(messages.getMessage("form.errors.fieldUpdateUnauthorized"));
    }
    RSForm original = null;
    if (form.isTemporary()) {
      original = formDao.getOriginalFromTemporaryForm(form.getId());
    }
    if (form.isTemporary() ? original == null : !form.isNewState()) {
      throw new IllegalStateException(
          messages.getMessage("form.errors.notBeingEdited", new Object[] {form.getId()}));
    }
    // The edit lock is held on whichever id was opened for editing: the original on a first edit,
    // the draft when editing resumes. Check both, and never acquire it here.
    assertNotEditedByOthers(form, user);
    if (original != null) {
      assertNotEditedByOthers(original, user);
    }
    fieldListSaver.apply(
        form, fields, protectCopiedFields ? getCopiedTemporaryFieldIds(form) : Set.of());
    form.setModifiedBy(user.getUsername());
    return formDao.save(form);
  }

  private void assertNotEditedByOthers(RSForm form, User user) {
    if (Boolean.TRUE.equals(formTracker.isSomeoneElseEditing(form, user.getUsername()))) {
      throw new IllegalStateException(
          messages.getMessage(
              "form.errors.notEditable",
              new Object[] {EditStatus.CANNOT_EDIT_OTHER_EDITING.name()}));
    }
  }

  @RequiresPermissions("FORM:CREATE")
  @Override
  public RSForm createForm(FormDefinitionCommand definition, User user) {
    RSForm form = create(user);
    form.setName(definition.name());
    form.setTags(definition.tags());
    if (definition.formType() != null) {
      form.setFormType(definition.formType());
    }
    // a new form's fields are all new, whatever ids a client sends
    List<FormFieldSaveItem> fields = new ArrayList<>();
    for (FormFieldSaveItem field : definition.fields()) {
      if (field == null) {
        fields.add(null); // reported by the field list check
        continue;
      }
      fields.add(field.withId(null));
    }
    return doSaveFields(form, fields, user, false);
  }

  @RequiresPermissions("FORM:READ") // the minimal permission, as for getForEditing
  @Override
  public RSForm editForm(
      Long formId, FormDefinitionCommand definition, User user, UserSessionTracker activeUsers) {
    RSForm original = get(formId, user);
    RSForm editing = getForEditing(formId, user, activeUsers, false);
    if (!editing.getEditStatus().isEditable()) {
      throw new IllegalStateException(
          messages.getMessage(
              "form.errors.notEditable", new Object[] {editing.getEditStatus().name()}));
    }
    List<FormFieldSaveItem> fields =
        toEditingFormIds(original, editing, definition.fields(), formId);
    editing = doSaveFields(editing, fields, user, false);
    editing.setName(definition.name());
    editing.setTags(definition.tags());
    editing = formDao.save(editing);
    if (!original.isNewState()) {
      Long draftId = editing.getId();
      editing = updateVersion(draftId, user);
      if (editing == null) {
        // unchecked, so the draft changes saved above roll back with the rest
        throw new FormVersionUpdateException(messages.getMessage("form.errors.versionNotUpdated"));
      }
    }
    return editing;
  }

  /**
   * API clients send the ids of the form they read. A form that has never been published is edited
   * in place, so its ids are kept; a published form is edited through its draft, and each original
   * field id is replaced by the id of its draft copy, found through the persisted {@code
   * tempFieldForm} link (fields reordered or added in a saved draft keep their link).
   */
  private List<FormFieldSaveItem> toEditingFormIds(
      RSForm original, RSForm editing, List<FormFieldSaveItem> fields, Long formId) {
    Map<Long, Long> editingIdByOriginalId = new LinkedHashMap<>();
    for (FieldForm field : original.getFieldForms()) {
      if (field.isDeleted()) {
        continue;
      }
      if (original.getId().equals(editing.getId())) {
        editingIdByOriginalId.put(field.getId(), field.getId());
      } else if (field.getTempFieldForm() instanceof FieldForm draftField
          && !draftField.isDeleted()) {
        if (draftField.getId() == null) {
          throw new IllegalStateException(
              "Draft copy of form field " + field.getId() + " has not been saved");
        }
        editingIdByOriginalId.put(field.getId(), draftField.getId());
      }
    }
    List<Long> requestedIds =
        fields.stream()
            .filter(Objects::nonNull)
            .map(FormFieldSaveItem::getId)
            .filter(Objects::nonNull)
            .toList();
    if (!editingIdByOriginalId.keySet().containsAll(requestedIds)) {
      throw new IllegalArgumentException(
          messages.getMessage(
              "errors.form.invalidFieldIds",
              new Object[] {
                formId,
                ListFormatUtils.formatList(
                    editingIdByOriginalId.keySet().stream().map(String::valueOf).toList()),
                ListFormatUtils.formatList(requestedIds.stream().map(String::valueOf).toList())
              }));
    }
    Set<Long> seen = new HashSet<>();
    List<FormFieldSaveItem> mapped = new ArrayList<>();
    for (FormFieldSaveItem field : fields) {
      if (field == null) {
        mapped.add(null); // reported by the field list check
        continue;
      }
      Long id = field.getId();
      if (id != null && !seen.add(id)) {
        throw new IllegalArgumentException(
            messages.getMessage("form.errors.fieldIdDuplicate", new Object[] {id}));
      }
      mapped.add(field.withId(id == null ? null : editingIdByOriginalId.get(id)));
    }
    return mapped;
  }
}
