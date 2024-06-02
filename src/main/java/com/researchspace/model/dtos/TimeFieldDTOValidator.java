package com.researchspace.model.dtos;

import static org.apache.commons.lang.StringUtils.isBlank;

import com.researchspace.model.field.TimeFieldForm;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/** Validates the business logic of the constraints on a T Field editor page. */
public class TimeFieldDTOValidator extends AbstractFieldFormValidator implements Validator {

  @Override
  public boolean supports(Class<?> clazz) {
    return clazz.isAssignableFrom(TimeFieldDTO.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public void validate(Object target, Errors errors) {
    super.validate(target, errors);
    TimeFieldDTO<TimeFieldForm> dto = (TimeFieldDTO) target;

    SimpleDateFormat df;
    try {
      df = new SimpleDateFormat(dto.getTimeFormat());
    } catch (IllegalArgumentException e) {
      errors.rejectValue("timeFormat", "errors.invalidDateFormattingString");
      return; // no point validating further if format is wrong.
    }

    // constraints are optional
    if (!isBlank(dto.getMaxValue())) {
      try {
        df.parse(dto.getMaxValue());
      } catch (ParseException e) {
        errors.rejectValue("maxValue", "errors.invalidDateFormat");
      }
    }
    if (!isBlank(dto.getMinValue())) {
      try {
        df.parse(dto.getMinValue());
      } catch (ParseException e) {
        errors.rejectValue("minValue", "errors.invalidDateFormat");
      }
    }
    if (!isBlank(dto.getDefaultValue())) {
      try {
        df.parse(dto.getDefaultValue());
      } catch (ParseException e) {
        errors.rejectValue("defaultValue", "errors.invalidDateFormat");
      }
    }
  }
}
