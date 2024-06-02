package com.researchspace.api.v1.model;

import com.researchspace.api.v1.controller.FormTemplatesCommon;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class ValidRadioFormFieldPostValidator
    implements ConstraintValidator<ValidRadioFormFieldPost, FormTemplatesCommon.RadioFieldPost> {

  @Override
  public void initialize(ValidRadioFormFieldPost constraintAnnotation) {}

  @Override
  public boolean isValid(
      FormTemplatesCommon.RadioFieldPost radioFF, ConstraintValidatorContext context) {
    boolean rc = true;
    if (radioFF.getDefaultOption() != null) {
      if (!radioFF.getOptions().contains(radioFF.getDefaultOption())) {
        rc = false;
      }
    }

    return rc;
  }
}
