package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.field.StringFieldForm;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonPropertyOrder(
    value = {"id", "globalId", "name", "type", "lastModified", "index", "defaultValue"})
public class ApiStringFormField extends ApiFormField {

  private String defaultValue;

  public ApiStringFormField(StringFieldForm formField) {
    super(formField);
    setDefaultValue(formField.getDefault());
  }
}
