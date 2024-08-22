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
   * @return
   */
  List<RSForm> getAllVisibleNormalForms();

  /**
   * Gets forms of a particular type.
   *
   * @param formType
   * @return
   */
  List<RSForm> getAllVisibleFormsByType(FormType formType);

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
   * @return
   */
  List<RSForm> getAllCurrentNormalForms();

  /**
   * Parameterized version of <code> getAllCurrentNormalForms()</code>
   *
   * @param type restrict the forms returned to the particular type
   * @return
   */
  List<RSForm> getAllCurrentFormsByType(FormType type);

  /**
   * Gets the most recent version of a form with a given template stable id
   *
   * @param stableid
   * @return
   */
  RSForm getMostRecentVersionForForm(String stableid);

  /**
   * Gets form list by permission, using Criteria API to sort permission.
   *
   * @param user
   * @return
   */
  ISearchResults<RSForm> getAllFormsByPermission(
      User user, FormSearchCriteria sc, PaginationCriteria<RSForm> pg);

  /**
   * Convenience method to get the system-default basic document form
   *
   * @return the {@link RSForm} representing a basic document, or <code>null</code> if could not be
   *     found.
   */
  RSForm getBasicDocumentForm();

  /**
   * Gets current version of a system Form with the given name:
   *
   * @param formName
   * @return An Optional Form.
   */
  Optional<RSForm> getCurrentSystemFormByName(String formName);

  RSForm findOldestFormByName(String name);

  /**
   * Boolean test for whether a user has created a form that has been used to create records by
   * other people.
   *
   * @param user
   * @return
   */
  boolean hasUserPublishedFormsUsedinOtherRecords(User user);

  RSForm findOldestFormByNameForCreator(String name, String username);

  /**
   * Removes all fields from a form, regardless of whether they have been set to deleted or not.
   *
   * <p>Usually this will be applied for a temporary form that is being reverted.
   *
   * @param formId
   * @return <code>true</code> if successfully deleted.
   */
  boolean removeFieldsFromForm(Long formId);

  /**
   * Getter with boolean choice as to whether to load form fields marked deleted or not,
   *
   * @param id
   * @param enableDeletedFilter
   * @return
   */
  RSForm get(Long id, boolean enableDeletedFilter);

  Long countDocsCreatedFromForm(RSForm form);

  /**
   * A user to be deleted can be the owner of forms which other users have created documents from.
   * To allow for the complete removal of a user, ownership of the form can be transferred to a
   * sysadmin
   *
   * @param toDelete - the user being deleted who owns forms that other users have created documents
   *     from
   * @param sysadmin - the sysadmin user that the ownership of those forms will be transferred to.
   */
  void transferOwnershipOfFormsToSysAdmin(User toDelete, User sysadmin);

  List<RSForm> getAllFormsOwnedByUser(User originalOwner);
}
