package com.researchspace.model.dtos;

import com.researchspace.model.field.RadioFieldForm;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RadioFieldDTO<T> extends AbstractFormFieldDTO<RadioFieldForm> {

  private String radioValues;
  private String radioSelected;
  private String fieldName;
  private boolean showAsPickList;
  private boolean sortAlphabetic;

  public RadioFieldDTO(
      String radioValues,
      String radioSelected,
      String fieldName,
      boolean showAsPickList,
      boolean sortAlphabetic) {
    this(radioValues, radioSelected, fieldName, showAsPickList, sortAlphabetic, false);
  }

  public RadioFieldDTO(
      String radioValues,
      String radioSelected,
      String fieldName,
      boolean showAsPickList,
      boolean sortAlphabetic,
      boolean isMandatory) {
    super(fieldName, isMandatory);
    this.radioValues = radioValues;
    this.radioSelected = radioSelected;
    this.fieldName = fieldName;
    this.showAsPickList = showAsPickList;
    this.sortAlphabetic = sortAlphabetic;
  }

  public void copyValuesIntoFieldForm(RadioFieldForm rfTemplate) {
    copyCommonValuesIntoFieldForm(rfTemplate);
    rfTemplate.setRadioOption(getRadioValues());
    rfTemplate.setDefaultRadioOption(getRadioSelected());
    rfTemplate.setModificationDate(new Date().getTime());
    rfTemplate.setShowAsPickList(this.showAsPickList);
    rfTemplate.setSortAlphabetic(this.sortAlphabetic);
  }

  @Override
  public RadioFieldForm createFieldForm() {
    RadioFieldForm fldemplate = new RadioFieldForm(getFieldName());
    copyValuesIntoFieldForm(fldemplate);
    return fldemplate;
  }
}
