package com.researchspace.dao;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.record.FormType;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.views.FormSearchCriteria;
import java.util.List;
import java.util.Optional;

/** DAO interface for creating record from templates */
public interface FormDao extends AbstractFormDao<RSForm, Long> {

  /**
   * Gets all Normal(Document) forms accessible to a user; i.e., excluding new or unpublished forms
   *
   */
  List<RSForm> getAllVisibleNormalForms();

  /**
   * Gets the original Form for this temporary form
   *
   * @param tempFormId An id for a temporary form
   * @return The owning Form, or <code>null</code> if none can be found.
   */
  RSForm getOriginalFromTemporaryForm(Long tempFormId);

  /**
   * Get all Forms that are the current version used to create new documents.
   *
   */
  List<RSForm> getAllCurrentNormalForms();

  /**
   * Parameterized version of <code> getAllCurrentNormalForms()</code>
   *
   * @param type restrict the forms returned to the particular type
   */
  List<RSForm> getAllCurrentFormsByType(FormType type);

  /**
   * Gets the most recent version of a form with a given template stable id
   *
   */
  RSForm getMostRecentVersionForForm(String stableId);

  /**
   * Gets form list by permission, using Criteria API to sort permission.
   *
   */
  ISearchResults<RSForm> getAllFormsByPermission(
      User user, FormSearchCriteria sc, PaginationCriteria<RSForm> pg);

  /**
   * Convenience method to get the system-default basic document form
   *
   * @return the {@link RSForm} representing a basic document, or <code>null</code> if the document could not be
   *     found.
   */
  RSForm getBasicDocumentForm();

  /**
   * Gets current version of a system Form with the given name:
   *
   * @return An Optional Form.
   */
  Optional<RSForm> getCurrentSystemFormByName(String formName);

  RSForm findOldestFormByName(String name);

  /**
   * Boolean test for whether a user has created a form that has been used to create records by
   * other people.
   *
   */
  boolean hasUserPublishedFormsUsedInOtherRecords(User user);

  RSForm findOldestFormByNameForCreator(String name, String username);

  /**
   * Removes all fields from a form, regardless of whether they have been set to deleted or not.
   *
   * <p>Usually this will be applied for a temporary form that is being reverted.
   *
   * @return <code>true</code> if successfully deleted.
   */
  boolean removeFieldsFromForm(Long formId);

  /**
   * Getter with boolean choice as to whether to load form fields marked deleted or not,
   *
   */
  RSForm get(Long id, boolean enableDeletedFilter);

  Long countDocsCreatedFromForm(RSForm form);

  /**
   * Transfer a list of forms from one user to another
   *
   * @param originalOwner - user who previously owned the forms
   * @param newOwner - user that the ownership of those forms will be transferred to
   * @param formIds - list of ids of the forms being transferred
   */
  void transferOwnershipOfForms(User originalOwner, User newOwner, List<Long> formIds);

  /***
   * Lists the forms created by formOwner which have had documents created by other users
   * from formOwners forms
   * @param formOwner the user who owns the forms
   * @return a List of RSForms used by other users
   */
  List<RSForm> getFormsUsedByOtherUsers(User formOwner);
}
