package com.researchspace.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

public class BasePaginationCriteriaTest {

  IPagination<Object> pg;

  @Test
  public void testSetPageNumberCannotBeNegative() {
    pg = new BasicPaginationCriteria<>(Object.class);
    assertThrows(IllegalArgumentException.class, () -> pg.setPageNumber(-1L));
  }

  @Test
  public void testResultsPerPAgeCannotBeNegative() {
    pg = new BasicPaginationCriteria<>(Object.class);
    assertThrows(IllegalArgumentException.class, () -> pg.setResultsPerPage(-2));
  }

  @Test
  public void testPaginationCriteriaOK() {
    pg = new BasicPaginationCriteria<>(Object.class);
    // these are OK
    pg.setOrderBy(null);
    pg.setOrderBy("");
    pg.setOrderBy("name");
    pg.setPageNumber(0L);
    pg.setResultsPerPage(4);
    pg.setSortOrder(SortOrder.ASC);
  }

  @Test
  public void testPaginationCriteriacreateForClassOK() {
    pg =
        BasicPaginationCriteria.createForClass(
            Object.class, "field1", SortOrder.ASC.toString(), 2L, 20);
    assertEquals(2, pg.getPageNumber().intValue());
    assertEquals(20, pg.getResultsPerPage().intValue());
    assertEquals(SortOrder.ASC, pg.getSortOrder());
    assertEquals("field1", pg.getOrderBy());
  }

  @Test
  public void setORderByRejectsSQLChars() {
    pg = new BasicPaginationCriteria<>(Object.class);
    pg.setOrderBy("'%%; delete * from User'");
    assertTrue(StringUtils.isEmpty(pg.getOrderBy()));
    // now an OK value:
    pg.setOrderBy("name");
    assertEquals("name", pg.getOrderBy());
  }

  @Test
  public void orderByIfNull() {
    pg = new BasicPaginationCriteria<>(Object.class);
    pg.setOrderByIfNull("'%%; delete * from User;'");
    assertTrue(StringUtils.isEmpty(pg.getOrderBy()));
    // now an OK value:
    pg.setOrderByIfNull("name");
    assertEquals("name", pg.getOrderBy());

    // once set, can't be overriddent
    pg.setOrderByIfNull("othername");
    assertEquals("name", pg.getOrderBy());
  }
}
