package com.researchspace.model.dtos;

import com.researchspace.model.field.TextFieldForm;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TextFieldDTO<T> extends AbstractFormFieldDTO<TextFieldForm> {
  private String defaultValue;

  public TextFieldDTO(String name, String defaultVal) {
    this(name, false, defaultVal);
  }

  public TextFieldDTO(String name, boolean isMandatory, String defaultVal) {
    super(name, isMandatory);
    this.defaultValue = defaultVal;
  }

  @Override
  public void copyValuesIntoFieldForm(TextFieldForm fldemplate) {
    copyCommonValuesIntoFieldForm(fldemplate);
    fldemplate.setDefaultValue(getDefaultValue());
  }

  @Override
  public TextFieldForm createFieldForm() {
    TextFieldForm tft = new TextFieldForm();
    copyValuesIntoFieldForm(tft);
    return tft;
  }
}
