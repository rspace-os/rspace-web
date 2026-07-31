package com.researchspace.api.v1.controller;

import com.researchspace.core.testutilJU5.JakartaValidatorTestJU5;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ApiPaginationCriteriaTest extends JakartaValidatorTestJU5 {

  DocumentApiPaginationCriteria pgCrit;

  @BeforeEach
  public void setUp() throws Exception {
    pgCrit = new DocumentApiPaginationCriteria();
  }

  @Test
  public void testOrderByValidation() {
    // all null fields are valid
    assertNErrors(pgCrit, 0);
    // valid ones are valid
    for (String orderby : DocumentApiPaginationCriteria.ALL_PARAMS) {
      pgCrit.setOrderBy(orderby);
      assertNErrors(pgCrit, 0);
    }
    // multiple spaces are ok
    pgCrit.setOrderBy("name    desc");
    assertNErrors(pgCrit, 0);

    pgCrit.setOrderBy("name2 desc");
    assertNErrors(pgCrit, 1);
  }

  @Test
  public void testPageingValidation() {
    // all null fields are valid
    assertNErrors(pgCrit, 0);
    pgCrit.setPageNumber(-1);
    assertNErrors(pgCrit, 1);

    pgCrit.setPageSize(0);
    assertNErrors(pgCrit, 2);
    pgCrit.setPageSize(DocumentApiPaginationCriteria.MAX_PAGE_SIZE + 1);
    assertNErrors(pgCrit, 2);
    pgCrit.setPageSize(DocumentApiPaginationCriteria.MAX_PAGE_SIZE);
    assertNErrors(pgCrit, 1);
  }
}
