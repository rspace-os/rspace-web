package com.researchspace.model.dtos;

import com.researchspace.model.field.FieldForm;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Abstract data transfer object for {@link FieldForm} editing and creation.
 *
 * @param <T>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class AbstractFormFieldDTO<T extends FieldForm> implements FormFieldSource<T> {
  /*
   * database constraint for form field name
   */
  public static final int MAX_NAME_LENGTH = 50;

  private String name;

  private boolean isMandatory;

  protected void copyCommonValuesIntoFieldForm(FieldForm fieldForm) {
    fieldForm.setName(getName());
    fieldForm.setMandatory(isMandatory());
  }
}
