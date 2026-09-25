package com.axiope.search;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SearchConfigTest {

  SearchConfig srchCfg;

  @BeforeEach
  public void setUp() throws Exception {
    srchCfg = new WorkspaceSearchConfig(TestFactory.createAnyUser("aa"));
  }

  @Test
  public void testSetPageNumberNotNegative() {
    assertThrows(IllegalArgumentException.class, () -> srchCfg.setPageNumber(-2));
  }

  @Test
  public void testSetPageSizeNotNegative() {
    assertThrows(IllegalArgumentException.class, () -> srchCfg.setPageSize(-3));
  }
}
