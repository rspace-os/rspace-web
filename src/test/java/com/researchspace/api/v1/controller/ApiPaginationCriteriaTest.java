package com.researchspace.api.v1.controller;

import com.researchspace.core.testutil.JavaxValidatorTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ApiPaginationCriteriaTest extends JavaxValidatorTest {

  DocumentApiPaginationCriteria pgCrit;

  @Before
  public void setUp() throws Exception {
    pgCrit = new DocumentApiPaginationCriteria();
  }

  @After
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
}
