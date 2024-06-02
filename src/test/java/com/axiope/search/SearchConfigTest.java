package com.axiope.search;

import com.researchspace.model.record.TestFactory;
import org.junit.Before;
import org.junit.Test;

public class SearchConfigTest {

  SearchConfig srchCfg;

  @Before
  public void setUp() throws Exception {
    srchCfg = new WorkspaceSearchConfig(TestFactory.createAnyUser("aa"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSetPageNumberNotNegative() {
    srchCfg.setPageNumber(-2);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSetPageSizeNotNegative() {
    srchCfg.setPageSize(-3);
  }
}
