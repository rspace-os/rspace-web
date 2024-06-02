package com.researchspace.api.v1.model;

import com.researchspace.api.v1.controller.FormTemplatesCommon;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/** Validates posted DateFieldForms for /forms POST */
public class ValidDateFormFieldPostValidator
    implements ConstraintValidator<ValidDateFormFieldPost, FormTemplatesCommon.DateFieldPost> {

  @Override
  public void initialize(ValidDateFormFieldPost constraintAnnotation) {}

  @Override
  public boolean isValid(
      FormTemplatesCommon.DateFieldPost radioFF, ConstraintValidatorContext context) {
    boolean rc = true;
    if (radioFF.getMin() != null && radioFF.getMax() != null) {
      rc = radioFF.getMin().before(radioFF.getMax());
    }
    if (rc && radioFF.getMin() != null && radioFF.getDefaultValue() != null) {
      rc = radioFF.getDefaultValue().after(radioFF.getMin());
    }
    if (rc && radioFF.getMax() != null && radioFF.getDefaultValue() != null) {
      rc = radioFF.getDefaultValue().before(radioFF.getMax());
    }
    return rc;
  }
}
