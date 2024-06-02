package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.field.ReferenceFieldForm;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonPropertyOrder(value = {"id", "globalId", "name", "type", "lastModified", "index"})
public class ApiReferenceFormField extends ApiFormField {

  private String defaultValue;

  public ApiReferenceFormField(ReferenceFieldForm formField) {
    super(formField);
  }
}
