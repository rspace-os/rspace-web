package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v2.model.ApiV2PaginationCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;

class ApiV2PaginationCriteriaValidatorTest {

  private final ApiV2PaginationCriteriaValidator validator = new ApiV2PaginationCriteriaValidator();

  @Test
  void acceptsValidPagination() {
    ApiV2PaginationCriteria criteria = new ApiV2PaginationCriteria();
    BeanPropertyBindingResult errors = new BeanPropertyBindingResult(criteria, "pagination");

    validator.validate(criteria, errors);

    assertFalse(errors.hasErrors());
  }

  @Test
  void rejectsNonPositivePageAndOutOfRangeLimit() {
    ApiV2PaginationCriteria criteria = new ApiV2PaginationCriteria();
    criteria.setPage(0);
    criteria.setLimit(ApiV2PaginationCriteria.MAX_LIMIT + 1);
    BeanPropertyBindingResult errors = new BeanPropertyBindingResult(criteria, "pagination");

    validator.validate(criteria, errors);

    assertTrue(errors.hasFieldErrors("page"));
    assertTrue(errors.hasFieldErrors("limit"));
  }
}
