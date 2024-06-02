package com.researchspace.model.dtos;

import com.researchspace.model.field.DateFieldForm;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.commons.lang.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/** Validates the business logic of the constraints on a Date Field editor page. */
public class DateFieldDTOValidator extends AbstractFieldFormValidator implements Validator {

  @Override
  public boolean supports(Class<?> clazz) {
    return clazz.isAssignableFrom(DateFieldDTO.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public void validate(Object target, Errors errors) {
    super.validate(target, errors);
    DateFieldDTO<DateFieldForm> dto = (DateFieldDTO) target;
    SimpleDateFormat df = null;
    try {
      df = new SimpleDateFormat(dto.getDateFormat());
    } catch (IllegalArgumentException e) {
      errors.rejectValue("dateFormat", "errors.invalidDateFormattingString");
      return; // no point validating further if format is wrong.
    }

    Date min = null, max = null, defaultDate = null;

    // constraints are optional
    if (!StringUtils.isBlank(dto.getMaxValue())) {
      try {
        max = df.parse(dto.getMaxValue());
      } catch (ParseException e) {
        errors.rejectValue("maxValue", "errors.invalidDateFormat");
      }
    }
    if (!StringUtils.isBlank(dto.getMinValue())) {
      try {
        min = df.parse(dto.getMinValue());
      } catch (ParseException e) {
        errors.rejectValue("minValue", "errors.invalidDateFormat");
      }
    }
    if (!StringUtils.isBlank(dto.getDefaultValue())) {
      try {
        defaultDate = df.parse(dto.getDefaultValue());
      } catch (ParseException e) {
        errors.rejectValue("defaultValue", "errors.invalidDateFormat");
      }
    }
    // global errors.
    if (min != null && max != null) {
      if (min.after(max)) {
        errors.reject("errors.minDateLaterThanMaxDate");
      }
      if (defaultDate != null) {
        if (defaultDate.before(min) || defaultDate.after(max)) {
          errors.reject("errors.dateOutsideAllowedRange");
        }
      }
    } else if (min != null && defaultDate != null) {
      if (defaultDate.before(min)) {
        errors.reject("errors.dateOutsideAllowedRange");
      }
    } else if (max != null && defaultDate != null) {
      if (defaultDate.after(max)) {
        errors.reject("errors.dateOutsideAllowedRange");
      }
    }
  }
}
