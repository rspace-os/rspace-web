package com.researchspace.model.dtos;

import com.researchspace.model.field.NumberFieldForm;
import java.math.BigInteger;
import org.apache.commons.lang.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * Validates the business logic of the constraints on a Number Field editor page when setting or
 * creating a form.
 */
public class NumberFieldDTOValidator extends AbstractFieldFormValidator implements Validator {

  @Override
  public boolean supports(Class<?> clazz) {
    return clazz.isAssignableFrom(NumberFieldDTO.class);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void validate(Object target, Errors errors) {
    super.validate(target, errors);
    NumberFieldDTO<NumberFieldForm> dto = (NumberFieldDTO<NumberFieldForm>) target;

    boolean decimalOK = true;
    if (!StringUtils.isEmpty(dto.getDecimalPlaces())) {
      try {
        // check num decimal places is a sensible value
        BigInteger test = new BigInteger(dto.getDecimalPlaces());
        if (test.compareTo(BigInteger.valueOf(Byte.MAX_VALUE)) == 1
            || test.compareTo(BigInteger.ZERO) == -1) {
          errors.rejectValue("decimalPlaces", "errors.number.decimalPlaces");
        }
        Byte.parseByte(dto.getDecimalPlaces());
      } catch (NumberFormatException nfe) {
        errors.rejectValue("decimalPlaces", "errors.number.decimalPlaces");
        decimalOK = false;
      }
    }

    boolean hasMax = false;
    boolean hasMin = false;
    boolean hasDef = false;
    if (hasMaxValue(dto)) {
      hasMax = true;
      NumberUtils.validateNumber("maxNumberValue", dto.getMaxNumberValue(), errors);
    }
    if (hasMinValue(dto)) {
      hasMin = true;
      NumberUtils.validateNumber("minNumberValue", dto.getMinNumberValue(), errors);
    }
    if (hasDefaultValue(dto)) {
      hasDef = true;
      NumberUtils.validateNumber("defaultNumberValue", dto.getDefaultNumberValue(), errors);
    }
    if (hasMin && hasMax && decimalOK && numbersOK(errors)) {
      Double min = Double.parseDouble(dto.getMinNumberValue());
      Double max = Double.parseDouble(dto.getMaxNumberValue());

      if (min > max) {
        errors.reject("errors.min_gt_max");
      }
      if (hasDef) {
        Double defaultVal = Double.parseDouble(dto.getDefaultNumberValue());
        if (defaultVal > max || defaultVal < min) {
          errors.reject("errors.disorderedRange");
        }
      }
    } else if (hasMin && decimalOK && numbersOK(errors)) {
      Double min = Double.parseDouble(dto.getMinNumberValue());
      if (hasDef) {
        Double defaultVal = Double.parseDouble(dto.getDefaultNumberValue());
        if (defaultVal < min) {
          errors.reject("errors.disorderedRange");
        }
      }
    } else if (hasMax && decimalOK && numbersOK(errors)) {
      Double max = Double.parseDouble(dto.getMaxNumberValue());
      if (hasDef) {
        Double defaultVal = Double.parseDouble(dto.getDefaultNumberValue());
        if (defaultVal > max) {
          errors.reject("errors.disorderedRange");
        }
      }
    }
  }

  private boolean hasDefaultValue(NumberFieldDTO<NumberFieldForm> dto) {
    return !StringUtils.isEmpty(dto.getDefaultNumberValue());
  }

  private boolean hasMinValue(NumberFieldDTO<NumberFieldForm> dto) {
    return !StringUtils.isEmpty(dto.getMinNumberValue());
  }

  private boolean hasMaxValue(NumberFieldDTO<NumberFieldForm> dto) {
    return !StringUtils.isEmpty(dto.getMaxNumberValue());
  }

  private boolean numbersOK(Errors errors) {
    if (errors.getFieldError("maxNumberValue") == null
        && errors.getFieldError("minNumberValue") == null
        && errors.getFieldError("defaultNumberValue") == null) {
      return true;
    }
    return false;
  }
}
