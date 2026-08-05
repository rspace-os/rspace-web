package com.researchspace.webapp.controller;

import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

public class LoginValidator implements Validator {

  public boolean supports(Class<?> aClass) {
    return LoginCommand.class.isAssignableFrom(aClass);
  }

  public void validate(Object o, Errors errors) {
    ValidationUtils.rejectIfEmptyOrWhitespace(errors, "username", "login.errors.usernameRequired");
    ValidationUtils.rejectIfEmptyOrWhitespace(errors, "password", "login.errors.passwordRequired");
  }
}
