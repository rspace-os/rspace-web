package com.researchspace.service.impl;

import static java.lang.String.format;

import com.researchspace.api.v1.model.ApiFormInfo;
import com.researchspace.core.util.DefaultURLPaginator;
import com.researchspace.core.util.ISearchResults;
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
import com.researchspace.service.FormManager;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.session.UserSessionTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.annotation.RequiresPermissions;
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

  public FormManagerImpl(@Autowired FormDao formDao) {
    super(formDao);
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

  /** Gets all non-temporary, current Forms */
  @Override
  public ISearchResults<RSForm> getAllWithPermissions(
      User user, PermissionType action, boolean visibleOnly) {
    FormSearchCriteria crit = new FormSearchCriteria();
    crit.setPublishedOnly(visibleOnly);
    crit.setRequestedAction(action);
    PaginationCriteria<RSForm> pgcrit = PaginationCriteria.createDefaultForClass(RSForm.class);
    pgcrit.setGetAllResults();
    return formDao.getAllFormsByPermission(user, crit, pgcrit);
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
    formTracker.unlockRecord(form, user, SessionAttributeUtils::getSessionId);
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

  @SuppressWarnings("unused")
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
      throw new UnsupportedOperationException("Attempted to unpublish a system form");
    }
    boolean altered = false;
    if (!permissionUtils.isPermitted(form, PermissionType.SHARE, authUser)) {
      throw new AuthorizationException("Unauthorised attempt to publish form: " + form.getId());
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
          format(
              "Form %s has been used to create documents, and cannot be deleted.",
              form.getStableID()));
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
  public RSForm copy(Long formID, final User user, final IFormCopyPolicy policy) {
    log.info("Copying form with ID {}", formID);
    RSForm original = get(formID, user);
    RSForm copy = (RSForm) original.copy(policy);
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
      EditStatus es =
          calculateEditStatus(id, user, activeUsers, useSessionToCalculateEditStatus, formToEdit);
      formToEdit.setEditStatus(es);
    } else if (permissionUtils.isPermitted(formToEdit, PermissionType.READ, user)) {
      formToEdit.setEditStatus(EditStatus.CANNOT_EDIT_NO_PERMISSION);
    } else {
      formToEdit.setEditStatus(EditStatus.ACCESS_DENIED);
    }
    formToEdit.getFieldForms().size(); // initialize FTs from lazy-loading
    return formToEdit;
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
    formTracker.unlockRecord(form, user, SessionAttributeUtils::getSessionId);
  }

  @Override
  public void unlockForm(Long formId, User user) {
    if (formDao.exists(formId)) {
      formTracker.unlockRecord(formDao.get(formId), user, SessionAttributeUtils::getSessionId);
    }
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
    formTracker.removeLockedRecordInSession(sessionId);
  }

  @Override
  public FormUserMenu addFormToUserCreateMenu(User toAdd, Long formId, User subject) {
    log.info("Adding form {} to create menu of user {}", formId, toAdd.getUsername());
    RSForm form = formDao.get(formId);
    if (!form.isCurrent()) {
      throw new IllegalArgumentException(
          "Only the current version of a form can be added "
              + "to the CreateMenu - this is version "
              + form.getVersion()
              + " of form "
              + form.getId());
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

  /*
   * =============================
   *  ApiForm handling methods
   * =============================
   */

  @Override
  public RSForm retrieveFormForApiForm(User user, ApiFormInfo apiForm, FormType formType) {
    RSForm form = null;
    Long apiFormId = apiForm == null ? null : apiForm.retrieveFormIdFromApiForm();
    if (apiFormId == null) {
      form = getBasicDocumentForm();
    } else {
      try {
        form = get(apiFormId, user);
      } catch (ObjectRetrievalFailureException e) {
        throw new IllegalArgumentException(
            "Form with id ["
                + apiFormId
                + "] could not "
                + "be retrieved - possibly it has been deleted, does not exist, "
                + "or you do not have permission to access it.");
      }
    }
    return form;
  }

  @Override
  public RSForm findOldestFormByName(String formName) {
    return formDao.findOldestFormByName(formName);
  }
}
