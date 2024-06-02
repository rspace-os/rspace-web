package com.researchspace.model.dtos;

import com.researchspace.model.field.TimeFieldForm;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
public class TimeFieldDTO<T> extends AbstractFormFieldDTO<TimeFieldForm> {

  private String defaultValue;
  private String minValue;
  private String maxValue;

  private String timeFormat;

  public TimeFieldDTO(
      String defaultValue, String minValue, String maxValue, String timeFormat, String name) {
    super(name, false);
    this.defaultValue = defaultValue;
    this.minValue = minValue;
    this.maxValue = maxValue;
    this.timeFormat = timeFormat;
  }

  public void copyValuesIntoFieldForm(TimeFieldForm fldemplate) {
    copyCommonValuesIntoFieldForm(fldemplate);
    fldemplate.setTimeFormat(getTimeFormat());
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(fldemplate.getTimeFormat());
    try {
      if (!StringUtils.isEmpty(getDefaultValue())) {
        fldemplate.setDefaultTime(simpleDateFormat.parse(getDefaultValue()).getTime());
      }
      if (!StringUtils.isEmpty(getMinValue())) {
        fldemplate.setMinTime(simpleDateFormat.parse(getMinValue()).getTime());
      }
      if (!StringUtils.isEmpty(getMaxValue())) {
        fldemplate.setMaxTime(simpleDateFormat.parse(getMaxValue()).getTime());
      }
    } catch (ParseException e) {
      LoggerFactory.getLogger(TimeFieldDTO.class.getName())
          .error("Error parsing epoch second from string.", e);
    }

    fldemplate.setModificationDate(new Date().getTime());
  }

  @Override
  public TimeFieldForm createFieldForm() {
    TimeFieldForm tft = new TimeFieldForm();
    copyValuesIntoFieldForm(tft);
    return tft;
  }
}
