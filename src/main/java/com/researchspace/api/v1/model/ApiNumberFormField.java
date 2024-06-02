package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.field.NumberFieldForm;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonPropertyOrder(
    value = {
      "id",
      "globalId",
      "name",
      "type",
      "lastModified",
      "index",
      "min",
      "max",
      "decimalPlaces",
      "defaultValue"
    })
public class ApiNumberFormField extends ApiFormField {

  private Double min, max, defaultValue;
  private Byte decimalPlaces;

  public ApiNumberFormField(NumberFieldForm formField) {
    super(formField);
    this.min = formField.getMinNumberValue();
    this.max = formField.getMaxNumberValue();
    this.decimalPlaces = formField.getDecimalPlaces();
    this.defaultValue = formField.getDefaultNumberValue();
  }
}
