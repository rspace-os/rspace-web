package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.field.URIFieldForm;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonPropertyOrder(value = {"id", "globalId", "name", "type", "lastModified", "index"})
public class ApiUriFormField extends ApiFormField {

  private String defaultValue;

  public ApiUriFormField(URIFieldForm formField) {
    super(formField);
  }
}
