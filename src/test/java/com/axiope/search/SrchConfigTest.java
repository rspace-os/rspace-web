package com.axiope.search;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SrchConfigTest {

  SearchConfig srchCfg;

  @BeforeEach
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
