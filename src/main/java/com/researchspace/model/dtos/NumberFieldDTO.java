package com.researchspace.model.dtos;

import com.researchspace.model.field.FieldType;
import com.researchspace.model.field.NumberFieldForm;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang.StringUtils;

/**
 * A JavaBean DTO for a NumberField. Instance variable names are the same as in Javascript data
 * structures and should not be changed without checking the relevant Javascript files.
 *
 * @param <T>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
public class NumberFieldDTO<T> extends AbstractFormFieldDTO<NumberFieldForm> {

  private String minNumberValue;
  private String maxNumberValue;
  private String decimalPlaces;
  private String defaultNumberValue;
  private String parentId;
  private String id;

  private FieldType type;

  public FieldType getType() {
    return type;
  }

  public void addType(FieldType type) {
    this.type = type;
  }

  public NumberFieldDTO() {
    super();
    addType(FieldType.NUMBER);
  }

  /*
   * USed for test cases.
   */
  public NumberFieldDTO(
      String minNumberValue,
      String maxNumberValue,
      String decimalPlaces,
      String defaultNumberValue,
      FieldType type,
      String name) {
    super(name, false);
    this.minNumberValue = minNumberValue;
    this.maxNumberValue = maxNumberValue;
    this.decimalPlaces = decimalPlaces;
    this.defaultNumberValue = defaultNumberValue;
    this.type = type;
  }

  // common operations for save and edit number field

  public void copyValuesIntoFieldForm(NumberFieldForm numberField) {
    copyCommonValuesIntoFieldForm(numberField);
    if (!StringUtils.isEmpty(getMinNumberValue())) {
      numberField.setMinNumberValue(Double.parseDouble(getMinNumberValue()));
    }
    if (!StringUtils.isEmpty(getMaxNumberValue())) {
      numberField.setMaxNumberValue(Double.parseDouble(getMaxNumberValue()));
    }
    if (!StringUtils.isEmpty(getDefaultNumberValue())) {
      numberField.setDefaultNumberValue(Double.parseDouble(getDefaultNumberValue()));
    }

    if (!StringUtils.isEmpty(getDecimalPlaces())) {
      numberField.setDecimalPlaces(Byte.parseByte(getDecimalPlaces()));
    }
    numberField.setModificationDate(new Date().getTime());
  }

  @Override
  public NumberFieldForm createFieldForm() {

    NumberFieldForm fldemplate = new NumberFieldForm(getName());
    copyValuesIntoFieldForm(fldemplate);
    return fldemplate;
  }
}
