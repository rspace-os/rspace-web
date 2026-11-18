package com.researchspace.model.dtos;

import com.researchspace.model.field.TimeFieldForm;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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
    this(defaultValue, minValue, maxValue, timeFormat, name, false);
  }

  public TimeFieldDTO(
      String defaultValue,
      String minValue,
      String maxValue,
      String timeFormat,
      String name,
      boolean isMandatory) {
    super(name, isMandatory);
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
      // the editors always send every setting, so an empty one has been cleared (0 is unset)
      fldemplate.setDefaultTime(parseOrUnset(simpleDateFormat, getDefaultValue()));
      fldemplate.setMinTime(parseOrUnset(simpleDateFormat, getMinValue()));
      fldemplate.setMaxTime(parseOrUnset(simpleDateFormat, getMaxValue()));
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

  private static long parseOrUnset(SimpleDateFormat format, String value) throws ParseException {
    return StringUtils.isEmpty(value) ? 0 : format.parse(value).getTime();
  }
}
