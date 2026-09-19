package com.researchspace.dao.hibernate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class RSQueryBuilderTest {

  /**
   * WorkspaceSearchInputValidator trims each half before parsing it, so anything it accepts has to
   * split the same way here. Splitting on a separator-with-surrounding-whitespace pattern instead
   * left the padding at the two ends of the term in place, and the ISO-8601 parse in
   * getPredicateList then threw where the user should have seen a validation error.
   */
  @Test
  void dateRangeTermSplitDropsPaddingAtBothEnds() {
    assertArrayEquals(
        new String[] {"2020-01-10T00:00:00Z", "null"},
        RSQueryBuilder.splitDateRangeTerm(" 2020-01-10T00:00:00Z ; null "));
  }

  @Test
  void dateRangeTermSplitKeepsEmptyHalves() {
    assertArrayEquals(new String[] {"", ""}, RSQueryBuilder.splitDateRangeTerm(";"));
  }

  @Test
  void dateRangeTermSplitAcceptsCommaSeparator() {
    assertArrayEquals(
        new String[] {"2020-01-10T00:00:00Z", "2021-01-10T00:00:00Z"},
        RSQueryBuilder.splitDateRangeTerm("2020-01-10T00:00:00Z,2021-01-10T00:00:00Z"));
  }
}
