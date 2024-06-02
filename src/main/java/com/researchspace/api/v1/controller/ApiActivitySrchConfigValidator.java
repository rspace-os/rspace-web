package com.researchspace.api.v1.controller;

import com.researchspace.service.audit.search.AbstractAuditSrchConfigValidator;
import org.springframework.validation.Errors;

public class ApiActivitySrchConfigValidator extends AbstractAuditSrchConfigValidator {

  @Override
  public boolean supports(Class<?> clazz) {
    return ApiActivitySrchConfig.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    super.validate(target, errors);
  }
}
