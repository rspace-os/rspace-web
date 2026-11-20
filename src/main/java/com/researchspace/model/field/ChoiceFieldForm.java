package com.researchspace.model.field;

import com.researchspace.model.field.rule.ElnChoiceValues;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import java.util.Arrays;
import java.util.List;
import org.hibernate.envers.Audited;

@Entity
@DiscriminatorValue(FieldType.CHOICE_TYPE)
@Audited
public class ChoiceFieldForm extends FieldForm {

  public ChoiceFieldForm(String name) {
    super(name);
    setType(FieldType.CHOICE);
  }

  public ChoiceFieldForm() {
    setType(FieldType.CHOICE);
  }

  /** */
  private static final long serialVersionUID = 1974158800405393625L;

  private boolean multipleChoice;
  private String choiceOptions;
  private String defaultChoiceOption;

  public boolean isMultipleChoice() {
    return multipleChoice;
  }

  public void setMultipleChoice(boolean multipleChoice) {
    this.multipleChoice = multipleChoice;
  }

  @Column(length = FieldUtils.MAX_OPTIONS_LENGTH)
  @NotBlank(message = "{validation.errors.choiceOptionsRequired}")
  public String getChoiceOptions() {
    return choiceOptions;
  }

  public void setChoiceOptions(String choiceOptions) {
    this.choiceOptions = choiceOptions;
  }

  public String getDefaultChoiceOption() {
    return defaultChoiceOption;
  }

  public void setDefaultChoiceOption(String defaultChoiceOption) {
    this.defaultChoiceOption = defaultChoiceOption;
  }

  @Override
  public ChoiceFieldForm shallowCopy() {
    ChoiceFieldForm cft = new ChoiceFieldForm();
    copyPropertiesToCopy(cft);
    cft.multipleChoice = multipleChoice;
    cft.defaultChoiceOption = defaultChoiceOption;
    cft.choiceOptions = choiceOptions;
    return cft;
  }

  @Override
  public ChoiceField _createNewFieldFromForm() {
    ChoiceField cf = new ChoiceField(this);
    return cf;
  }

  @Transient
  public List<String> getChoiceOptionAsList() {
    return ElnChoiceValues.parse(getChoiceOptions());
  }

  @Transient
  public String getSummary() {
    StringBuffer sb = new StringBuffer();
    sb.append("Type: [" + getType() + "], ")
        .append("Choices: [" + Arrays.toString(getChoiceOptionAsList().toArray()) + "], ")
        .append(
            "Default selection: ["
                + Arrays.toString(getDefaultChoiceOptionAsList().toArray())
                + "] ");
    return sb.toString();
  }

  @Transient
  public List<String> getDefaultChoiceOptionAsList() {
    return ElnChoiceValues.parse(getDefaultChoiceOption());
  }

  @Override
  public ErrorList validate(String data) {
    ErrorList el = new ErrorList();
    if (data.length() > 0 && !FieldUtils.isValidRadioOrChoiceString(data)) {
      el.addErrorMsgCode("validation.fieldData.invalidChoice", data);
    }
    return el;
  }

  @Transient
  @Override
  public String getDefault() {
    return getDefaultChoiceOption();
  }
}
