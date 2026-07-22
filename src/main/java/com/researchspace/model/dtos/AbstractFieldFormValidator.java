package com.researchspace.model.dtos;

import static com.researchspace.model.dtos.AbstractFormFieldDTO.MAX_NAME_LENGTH;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

abstract class AbstractFieldFormValidator implements Validator {

  @Override
  public boolean supports(Class<?> clazz) {
    return clazz.isAssignableFrom(AbstractFormFieldDTO.class);
  }

  @Override
  public void validate(Object target, Errors errors) {
    ValidationUtils.rejectIfEmptyOrWhitespace(errors, "name", "errors.noValue.name");
    AbstractFormFieldDTO<?> dto = (AbstractFormFieldDTO) target;
    if (isNameTooLong(dto.getName())) {
      rejectTooLong(errors);
    }
  }

  void rejectTooLong(Errors errors) {
    errors.reject("errors.maxLength", new Object[] {"name", MAX_NAME_LENGTH}, null);
  }

  boolean isNameTooLong(String name) {
    return !StringUtils.isEmpty(name) && name.length() > MAX_NAME_LENGTH;
  }
}
