package com.researchspace.service;

import com.researchspace.model.EditStatus;
import com.researchspace.model.User;
import com.researchspace.model.dtos.FormFieldSource;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.record.AbstractForm;
import com.researchspace.model.record.FormType;
import com.researchspace.model.record.RSForm;
import com.researchspace.session.UserSessionTracker;
import java.util.List;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;

/**
 * Superinterface for Form and SampleTemplate managers
 *
 * @param <T>
 */
public interface AbstractFormManager<T extends AbstractForm> extends GenericManager<T, Long> {

  /**
   * Creates and persists a new empty form/template with only mandatory fields populated.
   *
   * @param user
   * @return The newly created form/template.
   */
  T create(User u);

  T save(T template, User user);

  T get(Long sampleTemplateId, User user);

  /**
   * Generic method for creating FieldForm objects using validated DTO objects. Creates the object
   * model and persists to database.<br>
   * Validation should be performed in the controller layer using DTOValidator objects.<br>
   * This method assumes that FormFieldSource has been validated already <br>
   * No arguments should be <code>null</code>.
   *
   * @param formFieldDto A DTO of the correct type for the FieldForm.
   * @param formID A formID, the DB id of the form that will hold the field.
   * @return The new, persisted FieldForm. Once the transaction commits the fieldform's ID will be
   *     set.
   */
  <F extends FieldForm, U extends FormFieldSource<F>> F createFieldForm(
      U formFieldDto, long formID, User subject);

  /**
   * Generic method for updating FieldForm objects using validated DTO objects with the new
   * information. Updates object model and persists to database.<br>
   * Validation should be performed in the controller layer using DTOValidator objects.<br>
   * No arguments should be <code>null</code>.
   *
   * @param dto A DTO of the correct type for the FieldForm.
   * @param fieldid A fieldid
   * @return The updated FieldForm identified by the fieldid.
   * @throws AuthorizationException if user doesn't have edit permission
   */
  <F extends FieldForm, U extends FormFieldSource<F>> F updateFieldForm(
      U dto, Long fieldid, User subject);

  /**
   * Loads a form with initialized FieldForms, performing a permissions check
   *
   * @param id
   * @param user
   * @return
   */
  T getWithPopulatedFieldForms(long id, User user);

  /**
   * Gets a Form for editing.
   *
   * <p>If the Form is in the NEW state, and has not previously been published, then that Form is
   * returned. Otherwise, a temporary copy is returned for editing, which will create a new version
   * of the form when it is published.
   *
   * <p>If the subject has edit permission, an edit lock is attempted for this form. May use session
   * to calculate edit status
   *
   * @param id
   * @param user
   * @return A {@link RSForm} with its {@link EditStatus} set.
   */
  T getForEditing(Long id, User user, UserSessionTracker activeUsers);

  /**
   * @param id
   * @param user
   * @param activeUsers
   * @param useSessionToCalculateEditStatus
   * @return
   */
  public T getForEditing(
      Long id, User user, UserSessionTracker activeUsers, boolean useSessionToCalculateEditStatus);

  /**
   * Removes a field from a {@link RSForm} and persists the changes. The <code>FieldForm</code>
   * is<em>not</em> deleted from the database, it is soft-deleted. <br>
   * If the field is not part of the <code>Form</code> then this operation has no effect.
   *
   * @param fieldFormId A {@link FieldForm}.
   * @throws AuthorizationException if not Write permission on form.
   */
  void deleteFieldFromForm(Long fieldFormId, User subject);

  FieldForm getField(Long fieldId);

  /**
   * Reorders field forms (RSPAC-282)
   *
   * @param formId The form to reorder
   * @param orderedFieldIds the new ordered list of fields
   * @param user the subject
   * @return the altered form
   * @throws AuthorizationException if user lacks edit permission on the form
   * @see RSForm#reorderFields(List)
   */
  T reorderFields(Long formId, List<Long> orderedFieldIds, User user);

  T updateVersion(Long tempFormId, User user);

  /**
   * Returns the FormType, given an ID, to be used to test which of form or sampleTemplate
   * implementation to use
   *
   * @param id
   * @return
   */
  Optional<FormType> getTypeById(Long id);
}
