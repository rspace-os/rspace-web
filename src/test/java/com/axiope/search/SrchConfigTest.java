package com.axiope.search;

import static org.junit.Assert.assertNotNull;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

public class SrchConfigTest {

  SearchConfig srchCfg;

  @Before
  public void setUp() throws Exception {
    srchCfg = new WorkspaceSearchConfig();
  }

  @Test
  public void testGetUserFilterList() {
    assertNotNull(srchCfg.getUsernameFilter());
    srchCfg.setUsernameFilter(Collections.emptyList());
    assertNotNull(srchCfg.getUsernameFilter());
  }
}
