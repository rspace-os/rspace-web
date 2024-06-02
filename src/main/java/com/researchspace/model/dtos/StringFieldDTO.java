package com.researchspace.model.dtos;

import com.researchspace.model.field.StringFieldForm;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class StringFieldDTO<T> extends AbstractFormFieldDTO<StringFieldForm> {

  private String ifPassword, defaultStringValue;

  public StringFieldDTO() {}

  public StringFieldDTO(String name, String ifPassword, String defaultStringValue) {
    this(name, false, ifPassword, defaultStringValue);
  }

  public StringFieldDTO(
      String name, boolean isMandatory, String ifPassword, String defaultStringValue) {
    super(name, isMandatory);
    this.ifPassword = ifPassword;
    this.defaultStringValue = defaultStringValue;
  }

  public void copyValuesIntoFieldForm(StringFieldForm stringField) {
    copyCommonValuesIntoFieldForm(stringField);
    stringField.setDefaultStringValue(getDefaultStringValue());
    if (getIfPassword().equals("yes")) {
      stringField.setIfPassword(true);
    } else {
      stringField.setIfPassword(false);
    }
    stringField.setModificationDate(new Date().getTime());
  }

  @Override
  public StringFieldForm createFieldForm() {
    StringFieldForm rft = new StringFieldForm();
    copyValuesIntoFieldForm(rft);
    return rft;
  }
}
