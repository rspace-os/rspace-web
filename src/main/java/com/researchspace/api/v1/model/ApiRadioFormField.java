package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.field.RadioFieldForm;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonPropertyOrder(
    value = {"id", "globalId", "name", "type", "lastModified", "index", "defaultOption", "options"})
public class ApiRadioFormField extends ApiFormField {

  private List<String> options = new ArrayList<>();
  private String defaultOption;

  public ApiRadioFormField(RadioFieldForm radioFormField) {
    super(radioFormField);
    this.defaultOption = radioFormField.getDefaultRadioOption();
    this.options = radioFormField.getRadioOptionAsList();
  }
}
