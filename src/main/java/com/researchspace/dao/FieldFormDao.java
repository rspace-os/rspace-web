package com.researchspace.dao;

import com.researchspace.model.field.FieldForm;
import java.util.List;

public interface FieldFormDao extends GenericDao<FieldForm, Long> {

  /**
   * The field keys of every field of a form, including soft-deleted fields, so that a new field's
   * key can be checked for uniqueness against all of them.
   *
   * @param formId the form's id
   * @return the non-null keys, in no particular order
   */
  List<String> getFieldKeysForForm(Long formId);
}
