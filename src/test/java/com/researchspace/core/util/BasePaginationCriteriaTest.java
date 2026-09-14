package com.researchspace.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    assertThat(pg.getOrderBy()).isNullOrEmpty();
    // now an OK value:
    pg.setOrderBy("name");
    assertEquals("name", pg.getOrderBy());
  }

  @Test
  public void isOrderBySafeAcceptsLegitimateSortFields() {
    pg = new BasicPaginationCriteria<>(Object.class);
    for (String value :
        new String[] {
          "name",
          "id",
          "creationDate",
          "modificationDate",
          "owner.username",
          "owner.lastName",
          "communication.creationTime",
          "publishingState",
          "lastStatusUpdate",
          "deletedDate",
          "r.editInfo.name"
        }) {
      assertTrue(pg.isOrderBySafe(value), value + " should be allowed");
    }
    // null and empty are treated as 'no ordering requested', not as unsafe
    assertTrue(pg.isOrderBySafe(null));
    assertTrue(pg.isOrderBySafe(""));
  }

  @Test
  public void isOrderBySafeRejectsInjectionPayloads() {
    pg = new BasicPaginationCriteria<>(Object.class);
    for (String value :
        new String[] {
          "name,rand()",
          "id,(select 1)",
          "name; drop table user",
          "name asc, (select password from User)",
          "1=1",
          "name)",
          "name ",
          " name",
          "name/**/"
        }) {
      assertFalse(pg.isOrderBySafe(value), value + " should be rejected");
    }
  }

  @Test
  public void isOrderBySafeRejectsNonIdentifierTokens() {
    pg = new BasicPaginationCriteria<>(Object.class);
    // an identifier is dot-separated segments each starting with a letter or underscore
    for (String value :
        new String[] {
          "1", // bare digit: positional ordering in native SQL, not a property
          "123",
          ".",
          "name.", // trailing dot
          ".name", // leading dot
          "owner..name", // empty dot segment
          "1name" // leading digit
        }) {
      assertFalse(pg.isOrderBySafe(value), value + " should be rejected");
    }
  }

  @Test
  public void setOrderByDropsInjectionPayloadThatPassedTheOldBlacklist() {
    pg = new BasicPaginationCriteria<>(Object.class);
    // contains none of the previously blacklisted characters
    pg.setOrderBy("name,rand()");
    assertTrue(StringUtils.isEmpty(pg.getOrderBy()));
  }

  @Test
  public void sysadminVirtualSortTokensAreAcceptedVerbatim() {
    pg = new BasicPaginationCriteria<>(Object.class);
    // dispatch keys matched by equals() in SysAdminManagerImpl, never concatenated into SQL
    for (String token : new String[] {"fileUsage()", "recordCount()"}) {
      assertTrue(pg.isOrderBySafe(token), token + " should be allowed");
      pg.setOrderBy(token);
      assertEquals(token, pg.getOrderBy());
    }
    // no other parenthesised value is allowed
    assertFalse(pg.isOrderBySafe("rand()"));
    assertFalse(pg.isOrderBySafe("notAFunction()"));
    assertFalse(pg.isOrderBySafe("fileUsage(),rand()"));
  }

  @Test
  public void orderByIfNull() {
    pg = new BasicPaginationCriteria<>(Object.class);
    pg.setOrderByIfNull("'%%; delete * from User;'");
    assertThat(pg.getOrderBy()).isNullOrEmpty();
    // now an OK value:
    pg.setOrderByIfNull("name");
    assertEquals("name", pg.getOrderBy());

    // once set, can't be overriddent
    pg.setOrderByIfNull("othername");
    assertEquals("name", pg.getOrderBy());
  }
}
