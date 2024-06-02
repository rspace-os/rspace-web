package com.researchspace.model.dtos;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class TextFieldValidator extends AbstractFieldFormValidator implements Validator {

  @Override
  public boolean supports(Class<?> clazz) {
    return clazz.isAssignableFrom(TextFieldDTO.class);
  }

  @Override
  public void validate(Object target, Errors errors) {
    super.validate(target, errors);
  }
}
