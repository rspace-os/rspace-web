package com.researchspace.model.dtos;

import com.researchspace.model.field.AttachmentFieldForm;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Attachment fields have no settings beyond name and mandatory. They can be created only through
 * the forms API, but the form field editor renames, reorders, deletes and (un)marks them as
 * mandatory.
 */
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AttachmentFieldDTO extends AbstractFormFieldDTO<AttachmentFieldForm> {

  public AttachmentFieldDTO(String name, boolean isMandatory) {
    super(name, isMandatory);
  }

  @Override
  public void copyValuesIntoFieldForm(AttachmentFieldForm fieldForm) {
    copyCommonValuesIntoFieldForm(fieldForm);
  }

  @Override
  public AttachmentFieldForm createFieldForm() {
    AttachmentFieldForm fieldForm = new AttachmentFieldForm(getName());
    copyValuesIntoFieldForm(fieldForm);
    return fieldForm;
  }
}
