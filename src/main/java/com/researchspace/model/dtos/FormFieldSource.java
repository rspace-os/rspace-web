package com.researchspace.model.dtos;

import com.researchspace.model.field.FieldForm;

/**
 * Defines methods to convert an external source of a FormField, e.g the UI or API to generate a
 * FormField of the correct type.
 */
public interface FormFieldSource<T extends FieldForm> {
  /**
   * This method assumes that the source has been validated.
   *
   * @param fldemplate
   */
  void copyValuesIntoFieldForm(T fldemplate);

  T createFieldForm();
}
