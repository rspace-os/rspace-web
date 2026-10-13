package com.researchspace.model.units;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Temperature constraint validator for Quantifiable objects annotated with @ValidTemperature */
public class TemperatureConstraintValidator
    implements ConstraintValidator<ValidTemperature, Quantifiable> {

  @Override
  public void initialize(ValidTemperature constraintAnnotation) {}

  @Override
  public boolean isValid(Quantifiable value, ConstraintValidatorContext context) {
    if (hasTemperatureUnitButNoNumber(value)) {
      // Rejected as before; only the message differs, since the annotation's default text
      // (absolute zero) names the wrong reason.
      context.disableDefaultConstraintViolation();
      context
          .buildConstraintViolationWithTemplate("{errors.inventory.temperature.valueRequired}")
          .addConstraintViolation();
      return false;
    }
    return TemperatureValidator.validate(value);
  }

  private static boolean hasTemperatureUnitButNoNumber(Quantifiable value) {
    return value != null
        && value.getNumericValue() == null
        && value.getUnitId() != null
        && RSUnitDef.exists(value.getUnitId())
        && RSUnitDef.getUnitById(value.getUnitId()).isTemperature();
  }
}
