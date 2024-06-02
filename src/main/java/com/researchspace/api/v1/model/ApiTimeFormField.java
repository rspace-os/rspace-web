package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.field.TimeFieldForm;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonPropertyOrder(
    value = {"id", "globalId", "name", "type", "lastModified", "index", "defaultTime"})
public class ApiTimeFormField extends ApiFormField {

  private Long defaultTime;

  public ApiTimeFormField(TimeFieldForm formField) {
    super(formField);
    setDefaultTime(formField.getDefaultTime());
  }
}
