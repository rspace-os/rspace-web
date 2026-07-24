package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.model.ApiV2PaginationCriteria;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class ApiV2PaginationCriteriaValidator implements Validator {

  @Override
  public boolean supports(Class<?> clazz) {
    return ApiV2PaginationCriteria.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    ApiV2PaginationCriteria criteria = (ApiV2PaginationCriteria) target;
    if (criteria.getPage() < 1) {
      errors.rejectValue("page", "errors.api.pagination.page.min");
    }
    if (criteria.getLimit() < 1 || criteria.getLimit() > ApiV2PaginationCriteria.MAX_LIMIT) {
      errors.rejectValue(
          "limit",
          "errors.api.pagination.limit.range",
          new Object[] {ApiV2PaginationCriteria.MAX_LIMIT},
          null);
    }
  }
}
