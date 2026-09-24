package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.core.testutil.JakartaValidatorTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ApiPaginationCriteriaTest extends JakartaValidatorTest {

  DocumentApiPaginationCriteria pgCrit;

  @BeforeEach
  public void setUp() throws Exception {
    pgCrit = new DocumentApiPaginationCriteria();
  }

  @AfterEach
  public void tearDown() throws Exception {}

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

  @Test
  public void previousPageNumberIsCentralised() {
    pgCrit.setPageNumber(2);

    assertEquals(1, pgCrit.previousPageNumber());
  }

  @Test
  public void firstPageHasNoPreviousPageNumber() {
    pgCrit.setPageNumber(0);

    assertThrows(IllegalStateException.class, () -> pgCrit.previousPageNumber());
  }
}
