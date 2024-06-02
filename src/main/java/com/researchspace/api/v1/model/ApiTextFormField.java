package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.field.TextFieldForm;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonPropertyOrder(
    value = {"id", "globalId", "name", "type", "lastModified", "index", "defaultValue"})
public class ApiTextFormField extends ApiFormField {

  private String defaultValue;

  public ApiTextFormField(TextFieldForm formField) {
    super(formField);
    setDefaultValue(formField.getDefaultValue());
  }
}
