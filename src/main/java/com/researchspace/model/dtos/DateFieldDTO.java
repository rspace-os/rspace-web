package com.researchspace.model.dtos;

import com.researchspace.model.field.DateFieldForm;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

@Data
@EqualsAndHashCode(callSuper = false)
public class DateFieldDTO<T> extends AbstractFormFieldDTO<DateFieldForm> {
  private String defaultValue;
  private String minValue;
  private String maxValue;
  private String dateFormat;

  public DateFieldDTO(
      String defaultValue, String minValue, String maxValue, String dateFormat, String name) {
    super(name, false);
    this.defaultValue = defaultValue;
    this.minValue = minValue;
    this.maxValue = maxValue;
    this.dateFormat = dateFormat;
  }

  public void copyValuesIntoFieldForm(DateFieldForm fldemplate) {
    copyCommonValuesIntoFieldForm(fldemplate);

    fldemplate.setFormat(getDateFormat());
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(fldemplate.getFormat());
    try {
      if (!StringUtils.isEmpty(getDefaultValue())) {
        fldemplate.setDefaultDate(simpleDateFormat.parse(getDefaultValue()).getTime());
      } else {
        fldemplate.setDefaultDate(0);
      }
      if (!StringUtils.isEmpty(getMinValue())) {
        fldemplate.setMinValue(simpleDateFormat.parse(getMinValue()).getTime());
      } else {
        fldemplate.setMinValue(0);
      }
      if (!StringUtils.isEmpty(getMaxValue())) {
        fldemplate.setMaxValue(simpleDateFormat.parse(getMaxValue()).getTime());
      } else {
        fldemplate.setMaxValue(0);
      }
    } catch (ParseException e) {
      LoggerFactory.getLogger(DateFieldDTO.class.getName())
          .error("Error parsing epoch second from string.", e);
    }
    fldemplate.setModificationDate(new Date().getTime());
  }

  @Override
  public DateFieldForm createFieldForm() {
    DateFieldForm fldemplate = new DateFieldForm(getName());
    copyValuesIntoFieldForm(fldemplate);
    return fldemplate;
  }
}
