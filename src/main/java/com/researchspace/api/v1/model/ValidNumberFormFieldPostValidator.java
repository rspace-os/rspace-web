package com.researchspace.api.v1.model;

import com.researchspace.api.v1.controller.FormTemplatesCommon;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class ValidNumberFormFieldPostValidator
    implements ConstraintValidator<ValidNumberFormFieldPost, FormTemplatesCommon.NumberFieldPost> {

  @Override
  public void initialize(ValidNumberFormFieldPost constraintAnnotation) {}

  @Override
  public boolean isValid(
      FormTemplatesCommon.NumberFieldPost numberFF, ConstraintValidatorContext context) {
    boolean rc = true;
    if (numberFF.getMin() != null && numberFF.getMax() != null) {
      rc = numberFF.getMin() < numberFF.getMax();
    }
    if (rc && numberFF.getDefaultValue() != null) {
      if (numberFF.getMin() != null) {
        rc = numberFF.getDefaultValue() >= numberFF.getMin();
      }
      if (rc && numberFF.getMax() != null) {
        rc = numberFF.getDefaultValue() <= numberFF.getMax();
      }
    }

    return rc;
  }
}
