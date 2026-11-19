package com.researchspace.model.dtos;

import com.researchspace.model.field.StringFieldForm;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class StringFieldDTO<T> extends AbstractFormFieldDTO<StringFieldForm> {

  private String defaultStringValue;

  public StringFieldDTO() {}

  public StringFieldDTO(String name, String defaultStringValue) {
    this(name, false, defaultStringValue);
  }

  public StringFieldDTO(String name, boolean isMandatory, String defaultStringValue) {
    super(name, isMandatory);
    this.defaultStringValue = defaultStringValue;
  }

  public void copyValuesIntoFieldForm(StringFieldForm stringField) {
    copyCommonValuesIntoFieldForm(stringField);
    stringField.setDefaultStringValue(getDefaultStringValue());
    stringField.setModificationDate(new Date().getTime());
  }

  @Override
  public StringFieldForm createFieldForm() {
    StringFieldForm rft = new StringFieldForm();
    copyValuesIntoFieldForm(rft);
    return rft;
  }
}
