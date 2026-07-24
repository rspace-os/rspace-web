package com.researchspace.api.v1.model;

import com.researchspace.api.v1.controller.FormTemplatesCommon;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidChoiceFormFieldPostValidator
    implements ConstraintValidator<ValidChoiceFormFieldPost, FormTemplatesCommon.ChoiceFieldPost> {

  @Override
  public void initialize(ValidChoiceFormFieldPost constraintAnnotation) {}

  @Override
  public boolean isValid(
      FormTemplatesCommon.ChoiceFieldPost choiceFF, ConstraintValidatorContext context) {
    boolean rc =
        choiceFF.getDefaultOptions().stream()
            .allMatch(defaultOption -> choiceFF.getOptions().contains(defaultOption));
    return rc;
  }
}
