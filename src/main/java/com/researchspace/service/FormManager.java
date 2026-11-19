package com.researchspace.service;

import com.researchspace.api.v1.model.ApiFormInfo;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.FormFieldSaveItem;
import com.researchspace.model.dtos.FormMenu;
import com.researchspace.model.dtos.FormSharingCommand;
import com.researchspace.model.record.FormType;
import com.researchspace.model.record.FormUserMenu;
import com.researchspace.model.record.IFormCopyPolicy;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.FormSearchCriteria;
import com.researchspace.session.UserSessionTracker;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Service API for operating on {@link RSForm} objects */
public interface FormManager extends AbstractFormManager<RSForm> {

  /**
   * Makes a copy of the original Template
   *
   * @param templateID The id of the template to be copied
   * @param user A non-null User
   * @param policy The copy policy to use
   * @return The copy of the Template.
   * @throws Exception
   */
  RSForm copy(Long templateID, User user, IFormCopyPolicy<RSForm> policy);

  /**
   * Loads a {@link RSForm} of a given ID
   *
   * @param id
   * @param user
   * @return
   * @throws Exception
   */
  RSForm get(Long id, User user);

  RSForm get(long id, User user, boolean includeDeleted);

  /**
   * Allows deletion only if the Form is in NEW state or is unused to create documents
   *
   * @param formId ID of form to delete
   * @param user
   * @return the deleted form, for reference. Will be Optional.empty if form does not exist.
   */
  Optional<RSForm> delete(long formId, User user);

  boolean formExistsInMenuForUser(String formStableID, User user);

  /**
   * Gets all the current Forms of Normal type. Does not include old versions.
   *
   * @return
   */
  List<RSForm> getAllCurrentNormalForms();

  /** Retrieves system Basic Document form */
  RSForm getBasicDocumentForm();

  /**
   * Get current system form by name
   *
   * @param name
   * @return An Optional<RSForm>
   */
  Optional<RSForm> getCurrentSystemForm(String name);

  /**
   * Updates the form's last save time.
   *
   * @param formID
   * @param user
   * @return The saved form
   */
  RSForm save(RSForm form, User user);

  /**
   * Publishes /unpublishes a Form.
   *
   * @param id The id of the Form to publish.
   * @param toPublish - <code>true</code> = Publish, <code>false</code>= Unpublish.
   * @param formShareCommand An optional FormSharingCommand with access configuration
   * @return The updated form.
   */
  RSForm publish(Long id, boolean toPublish, FormSharingCommand formShareCommand, User authUser);

  /**
   * Updates a form's group eead/write permissions.
   *
   * @param templateid
   * @param config
   * @return
   */
  RSForm updatePermissions(Long formId, FormSharingCommand config, User authUser);

  /**
   * @param user
   * @param sc
   * @param pg
   * @return
   */
  ISearchResults<RSForm> searchForms(
      User user, FormSearchCriteria sc, PaginationCriteria<RSForm> pg);

  /**
   * Gets a {@link List} of {@link RSForm} objects that have been recently used to create {@link
   * StructuredDocument}s.
   */
  List<RSForm> getDynamicMenuFormItems(User user);

  // extension for change icon
  RSForm getForEditing(long id, User user, UserSessionTracker activeUsers, long iconId);

  /**
   * Releases one of the user's edit-lock acquisitions on the form, in the current session, by id.
   * Acquisitions held by a form editor page's token (see {@link #getForEditorPage}) are left alone,
   * so this can only release a lock taken without a token.
   */
  void unlockForm(RSForm form, User user);

  /** As {@link #unlockForm(RSForm, User)}, for a form id; nothing happens for an unknown id. */
  void unlockForm(Long formId, User user);

  /**
   * Saves the form for a form editor page and releases only that page's edit lock, given its token.
   * With a blank token this is {@link #save}, which releases a lock by id.
   *
   * @param form the form to save
   * @param lockToken the token from {@link #getForEditorPage}, or blank
   * @param user the user saving, in the current session
   * @return the saved form
   */
  RSForm saveAndReleaseEditLock(RSForm form, String lockToken, User user);

  /**
   * A form opened in the form editor page, and the token for the page's hold on its edit lock.
   *
   * @param form the form to show, with its edit status
   * @param editLockToken the page's token, or null when the page did not get the lock
   */
  record FormEditorPage(RSForm form, String editLockToken) {}

  /**
   * Opens a form for the form editor page, as {@link #getForEditing(Long, User,
   * UserSessionTracker)}. When the page gets the edit lock, on {@code id} (the original on a first
   * edit of a published form, the draft when editing resumes), it also gets a token for its hold on
   * that lock, recorded in the same step, so a release by id can never take the lock first. The
   * hold lasts until the page releases it with {@link #releaseEditLock}, or until the session ends.
   *
   * @param id the form in the editor page's URL
   * @param user the user editing, in the current session
   * @param activeUsers the active users, for the edit lock
   * @return the form, and the page's token when it holds the lock
   */
  FormEditorPage getForEditorPage(Long id, User user, UserSessionTracker activeUsers);

  /**
   * Releases the edit lock that the page holding {@code token} acquired, at most once, whether or
   * not the form still exists. Nothing happens for a blank, unknown or already used token, or when
   * the caller is not the user and session the token was issued to, so a stale page (an old tab, or
   * a page left before a reload or a back-forward restore) can never release another page's lock.
   *
   * @param token the token from {@link #getForEditorPage}
   * @param user the user leaving the editor
   * @return whether a lock was released
   */
  boolean releaseEditLock(String token, User user);

  /**
   * Gets the IDs of fields on a temporary form that were copied from the original form.
   *
   * <p>Fields added directly to the temporary form are excluded.
   */
  Set<Long> getCopiedTemporaryFieldIds(RSForm form);

  /**
   * Reverts the form back to previous version, removing draft changes.
   *
   * @param tempFormId
   * @param user
   */
  void abandonUpdateForm(long tempFormId, User user);

  /**
   * Persists the tag for a form.
   *
   * @param recordId
   * @param tagtext
   * @return the saved form
   */
  RSForm saveFormTag(Long recordId, String tagtext);

  /**
   * Releases every edit lock taken in a session, and its form editor pages' holds, when the session
   * ends.
   */
  void removeFromEditorTracker(String sessionId);

  /**
   * Adds a current Form to a user's create menu.
   *
   * @param user
   * @param formId
   * @param subject
   * @return the created {@link FormUserMenu} entity
   */
  FormUserMenu addFormToUserCreateMenu(User user, Long formId, User subject);

  /**
   * Removes a Form from a user's create menu
   *
   * @param user
   * @param formId
   * @param subject
   * @return <code>true</code> if removed, <code>false</code>otherwise
   */
  boolean removeFormFromUserCreateMenu(User user, Long formId, User subject);

  /**
   * @param user
   * @return
   */
  FormMenu generateFormMenu(User user);

  /**
   * Retrieves RSForm for id provided in ApiForm, or throws exception if form doesn't exist.
   *
   * <p>apiForm value provided can be null, in that case default form is returned.
   */
  RSForm retrieveFormForApiForm(User user, ApiFormInfo form, FormType normal);

  RSForm findOldestFormByName(String formName);

  /**
   * Saves the full field list of a form being edited, in one transaction: creates fields without an
   * id, updates fields with one (by their id in this form), deletes saved fields the list omits,
   * applies the list order, and sets field keys and display rules. The whole result is validated
   * (field settings, keys, and every display rule against the final field set) before anything is
   * persisted; any problem rejects the whole save.
   *
   * <p>Does not release the edit lock, unlike {@link #save(RSForm, User)}: the user keeps editing.
   *
   * @param formId the form being edited: a draft of a published form, or a form that has never been
   *     published
   * @param fields every field the form should have, in order
   * @param user the subject, who needs write permission on the form
   * @return the saved form
   * @throws FormFieldsValidationException if the field list is invalid
   * @throws org.apache.shiro.authz.AuthorizationException if the user cannot write the form
   * @throws IllegalStateException if the form is not being edited, or another user is editing it
   */
  RSForm saveFields(Long formId, List<FormFieldSaveItem> fields, User user);

  /**
   * Saves the full field list of the form being edited, as {@link #saveFields(Long, List, User)}
   * does, but only if the field list is still the one the caller read: the form field editor sends
   * the version it was given, so a save from an out-of-date page cannot overwrite a newer save.
   * Saves of one form run one at a time.
   *
   * @param expectedVersion the {@link #getFieldsVersion(RSForm)} of the field list the caller read
   * @throws FormFieldsConflictException if the field list has changed since
   * @throws FormFieldsValidationException if the field list is invalid
   * @throws org.apache.shiro.authz.AuthorizationException if the user cannot write the form
   * @throws IllegalStateException if the form is not being edited, or another user is editing it
   */
  RSForm saveFields(Long formId, List<FormFieldSaveItem> fields, String expectedVersion, User user);

  /**
   * The version of the form's field list, which changes whenever a field is added, deleted, moved
   * or changed.
   */
  String getFieldsVersion(RSForm form);

  /** Whether the user may change the form, and so its fields. */
  boolean canWrite(RSForm form, User user);

  /**
   * Creates a form and all its fields in one transaction, for the forms API. A rejected request
   * leaves no form behind.
   *
   * @param definition the form; field ids are ignored
   * @param user the subject
   * @return the new form, in the NEW state
   * @throws FormFieldsValidationException if the field list is invalid
   */
  RSForm createForm(FormDefinitionCommand definition, User user);

  /**
   * Replaces a form's definition in one transaction, for the forms API: opens the form for editing,
   * applies the definition with {@link #saveFields(Long, List, User)} and, unless the form has
   * never been published, makes the result the form's new version. Field ids in the definition are
   * the ids of {@code formId}'s fields; on a published form they are mapped to its draft. As the
   * API always has, omitting a field deletes it, including a field of the published version.
   *
   * <p>If the form already has a draft that was saved (for example in the form field editor) but
   * not yet made a new version, the request replaces that draft: fields that exist only in the
   * draft are always deleted (the request can only name fields of {@code formId}, and a draft-only
   * field id is rejected as unknown), the draft's edits to listed fields are overwritten, and the
   * result is published as the new version. A rejected request leaves the form, any existing draft
   * and the edit lock as they were.
   *
   * @param formId the form to edit
   * @param definition the whole new definition
   * @param user the subject
   * @param activeUsers the active users, to judge whether someone else is editing
   * @return the edited form (the new version, if a version was made)
   * @throws IllegalStateException if the form cannot be edited
   * @throws FormVersionUpdateException if the draft cannot be made the new version (for example,
   *     the form is a system form); nothing is changed
   * @throws IllegalArgumentException if a field id is unknown or repeated
   * @throws FormFieldsValidationException if the field list is invalid
   */
  RSForm editForm(
      Long formId, FormDefinitionCommand definition, User user, UserSessionTracker activeUsers);
}
