package com.researchspace.service.impl;

import com.researchspace.dao.AbstractFormDao;
import com.researchspace.dao.FieldFormDao;
import com.researchspace.model.User;
import com.researchspace.model.dtos.FormFieldSource;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.AbstractForm;
import com.researchspace.model.record.FormType;
import com.researchspace.service.AbstractFormManager;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractFormManagerImpl<T extends AbstractForm>
    extends GenericManagerImpl<T, Long> implements AbstractFormManager<T> {

  @Autowired private AbstractFormDao<T, Long> absFormdao;
  @Autowired IPermissionUtils permissionUtils;
  @Autowired FieldFormDao fieldFormDao;
  @Autowired protected RecordEditorTracker formTracker;

  public AbstractFormManagerImpl(AbstractFormDao<T, Long> dao) {
    super(dao);
    this.absFormdao = dao;
    this.formTracker = new RecordEditorTracker();
  }

  T doPopulateCoreFields(T form, User user) {
    form.setCreatedBy(user.getUsername());
    form.setModifiedBy(user.getUsername());
    form.setOwner(user);
    form.setDescription("No description");
    form.setName("Untitled");
    if (!permissionUtils.isPermitted(form, PermissionType.CREATE, user)) {
      throw new AuthorizationException("You do not have permission to create a form");
    }
    return form;
  }

  @Override
  public T save(T form, User user) {
    form.setModifiedBy(user.getUsername());
    form.setModificationDate(new Date());
    T saved = absFormdao.save(form);
    return saved;
  }

  @Override
  public <F extends FieldForm, U extends FormFieldSource<F>> F createFieldForm(
      U dto, long formId, User subject) {
    T form = absFormdao.get(formId);
    if (!hasWritePermission(subject, form)) {
      throw new AuthorizationException("Create field form unauthorised");
    }
    F field = dto.createFieldForm();
    form.setModificationDate(new Date());

    setColumnIndexAndPersist(form, field);
    return field;
  }

  private T setColumnIndexAndPersist(T form, FieldForm fieldForm) {
    form.addFieldForm(fieldForm);
    fieldForm.setColumnIndex(form.getNumActiveFields() - 1);
    return absFormdao.save(form);
  }

  @Override
  public T getWithPopulatedFieldForms(long id, User user) {
    T form = absFormdao.get(id);
    permissionUtils.assertIsPermitted(form, PermissionType.READ, user, " read form ");
    form.getFieldForms().size();
    return form;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <F extends FieldForm, U extends FormFieldSource<F>> F updateFieldForm(
      U dto, Long fieldFormID, User subject) {
    F fieldform = (F) fieldFormDao.get(fieldFormID);
    T form = (T) fieldform.getForm();
    if (!hasWritePermission(subject, form)) {
      throw new AuthorizationException("Updating a field");
    }
    dto.copyValuesIntoFieldForm(fieldform);
    Date currTime = new Date();
    fieldform.setModificationDate(currTime.getTime());
    form.setModificationDate(currTime);

    fieldFormDao.save(fieldform);
    absFormdao.save(form);
    return fieldform;
  }

  protected boolean hasWritePermission(User user, T rc) {
    return permissionUtils.isPermitted(rc, PermissionType.WRITE, user);
  }

  @Override
  public void deleteFieldFromForm(Long fieldId, User subject) {

    FieldForm fieldToRemove = getField(fieldId);
    T form = (T) fieldToRemove.getForm();
    if (!permissionUtils.isPermitted(form, PermissionType.WRITE, subject)) {
      throw new AuthorizationException("Not permitted to delete field [" + fieldId + "]");
    }

    fieldToRemove.setDeleted(true);
    form.setModificationDate(new Date());

    absFormdao.save(form);
    fieldFormDao.save(fieldToRemove);
  }

  @Override
  public FieldForm getField(Long fieldId) {
    return fieldFormDao.get(fieldId);
  }

  @Override
  public T reorderFields(Long formId, List<Long> orderedFieldIds, User user) {
    T toEdit = get(formId, user);
    if (!hasWritePermission(user, toEdit)) {
      throw new AuthorizationException(
          "Unauthorised attempt to reorder form ["
              + formId
              + "] by user["
              + user.getUsername()
              + "]");
    }
    toEdit.reorderFields(orderedFieldIds);
    return save(toEdit, user);
  }

  public Optional<FormType> getTypeById(Long id) {
    return absFormdao.getTypeById(id);
  }
}
