package com.researchspace.model.dtos;

import com.researchspace.model.field.FieldForm;
import java.util.List;

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

  /**
   * The options of a radio or choice source that receives them one by one (the forms API), before
   * they are joined into the stored {@code fieldChoices=a&fieldChoices=b} string, so each option
   * can be checked on its own.
   *
   * @return the submitted options, or null if the source carries the stored option string itself or
   *     is not a radio or choice source
   */
  default List<String> submittedOptions() {
    return null;
  }
}
