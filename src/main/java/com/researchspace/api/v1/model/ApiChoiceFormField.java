package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.field.ChoiceFieldForm;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonPropertyOrder(value = {"id", "globalId", "name", "type", "lastModified", "index"})
public class ApiChoiceFormField extends ApiFormField {

  private boolean multipleChoice;
  private List<String> options = new ArrayList<>();
  private List<String> defaultOptions = new ArrayList<>();

  public ApiChoiceFormField(ChoiceFieldForm choiceFormField) {
    super(choiceFormField);
    this.multipleChoice = choiceFormField.isMultipleChoice();
    this.options = choiceFormField.getChoiceOptionAsList();
    this.defaultOptions = choiceFormField.getDefaultChoiceOptionAsList();
  }
}
