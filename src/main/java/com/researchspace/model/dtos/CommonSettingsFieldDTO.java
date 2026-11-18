package com.researchspace.model.dtos;

import com.researchspace.model.field.AttachmentFieldForm;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.ReferenceFieldForm;
import com.researchspace.model.field.URIFieldForm;
import java.util.function.Supplier;

/**
 * Source for field forms that have no type-specific settings, only the common name and mandatory
 * flag: URI, Reference and Attachment fields.
 */
public final class CommonSettingsFieldDTO<T extends FieldForm> extends AbstractFormFieldDTO<T> {

  private final Supplier<T> fieldFormFactory;

  private CommonSettingsFieldDTO(String name, boolean isMandatory, Supplier<T> fieldFormFactory) {
    super(name, isMandatory);
    this.fieldFormFactory = fieldFormFactory;
  }

  public static CommonSettingsFieldDTO<URIFieldForm> uri(String name, boolean isMandatory) {
    return new CommonSettingsFieldDTO<>(name, isMandatory, URIFieldForm::new);
  }

  public static CommonSettingsFieldDTO<ReferenceFieldForm> reference(
      String name, boolean isMandatory) {
    return new CommonSettingsFieldDTO<>(name, isMandatory, ReferenceFieldForm::new);
  }

  public static CommonSettingsFieldDTO<AttachmentFieldForm> attachment(
      String name, boolean isMandatory) {
    return new CommonSettingsFieldDTO<>(name, isMandatory, AttachmentFieldForm::new);
  }

  @Override
  public void copyValuesIntoFieldForm(T fieldForm) {
    copyCommonValuesIntoFieldForm(fieldForm);
  }

  @Override
  public T createFieldForm() {
    T fieldForm = fieldFormFactory.get();
    copyValuesIntoFieldForm(fieldForm);
    return fieldForm;
  }
}
