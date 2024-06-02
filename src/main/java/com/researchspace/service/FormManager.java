package com.researchspace.service;

import com.researchspace.api.v1.model.ApiFormInfo;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.FormMenu;
import com.researchspace.model.dtos.FormSharingCommand;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.FormType;
import com.researchspace.model.record.FormUserMenu;
import com.researchspace.model.record.IFormCopyPolicy;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.FormSearchCriteria;
import com.researchspace.session.UserSessionTracker;
import java.util.List;
import java.util.Optional;

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
  RSForm copy(Long templateID, User user, IFormCopyPolicy policy);

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
   * @deprecated use searchForms instead which paginates the response.
   */
  ISearchResults<RSForm> getAllWithPermissions(User user, PermissionType type, boolean visibleOnly);

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

  void unlockForm(RSForm form, User user);

  void unlockForm(Long formId, User user);

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
}
