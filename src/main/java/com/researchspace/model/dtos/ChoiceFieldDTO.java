package com.researchspace.model.dtos;

import com.researchspace.model.field.ChoiceFieldForm;
import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChoiceFieldDTO<T> extends AbstractFormFieldDTO<ChoiceFieldForm> {

  private String choiceValues;
  private String multipleChoice;
  private String selectedValues;
  private String fieldName;

  public ChoiceFieldDTO(
      String choiceValues, String multipleChoice, String selectedValues, String fieldName) {
    this(choiceValues, multipleChoice, selectedValues, fieldName, false);
  }

  public ChoiceFieldDTO(
      String choiceValues,
      String multipleChoice,
      String selectedValues,
      String fieldName,
      boolean isMandatory) {
    super(fieldName, isMandatory);
    this.choiceValues = choiceValues;
    this.multipleChoice = multipleChoice;
    this.selectedValues = selectedValues;
    this.fieldName = fieldName;
  }

  public void copyValuesIntoFieldForm(ChoiceFieldForm choiceField) {
    copyCommonValuesIntoFieldForm(choiceField);
    choiceField.setChoiceOptions(getChoiceValues());
    choiceField.setDefaultChoiceOption(getSelectedValues());
    if (getMultipleChoice().equals("yes")) {
      choiceField.setMultipleChoice(true);
    } else {
      choiceField.setMultipleChoice(false);
    }
    choiceField.setModificationDate(new Date().getTime());
  }

  @Override
  public ChoiceFieldForm createFieldForm() {
    ChoiceFieldForm choiceField = new ChoiceFieldForm(getFieldName());
    copyValuesIntoFieldForm(choiceField);
    return choiceField;
  }
}
