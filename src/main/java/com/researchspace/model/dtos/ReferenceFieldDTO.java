package com.researchspace.model.dtos;

import com.researchspace.model.field.ReferenceFieldForm;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Reference fields have no settings beyond name and mandatory. They can be created only through the
 * forms API, but the form field editor renames, reorders, deletes and (un)marks them as mandatory.
 */
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReferenceFieldDTO extends AbstractFormFieldDTO<ReferenceFieldForm> {

  public ReferenceFieldDTO(String name, boolean isMandatory) {
    super(name, isMandatory);
  }

  @Override
  public void copyValuesIntoFieldForm(ReferenceFieldForm fieldForm) {
    copyCommonValuesIntoFieldForm(fieldForm);
  }

  @Override
  public ReferenceFieldForm createFieldForm() {
    ReferenceFieldForm fieldForm = new ReferenceFieldForm(getName());
    copyValuesIntoFieldForm(fieldForm);
    return fieldForm;
  }
}
