package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateSerialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.model.field.DateFieldForm;
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
      "defaultValue",
      "min",
      "max"
    })
public class ApiDateFormField extends ApiFormField {

  @JsonSerialize(using = ISO8601DateSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long defaultValue;

  @JsonSerialize(using = ISO8601DateSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long min;

  @JsonSerialize(using = ISO8601DateSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long max;

  public ApiDateFormField(DateFieldForm dateFormField) {
    super(dateFormField);
    this.defaultValue = dateFormField.getDefaultDate();
    this.min = dateFormField.getMinValue();
    this.max = dateFormField.getMaxValue();
  }
}
