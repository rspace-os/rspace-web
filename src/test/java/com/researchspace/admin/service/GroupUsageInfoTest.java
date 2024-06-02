package com.researchspace.admin.service;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GroupUsageInfoTest {

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testGetPercent() {
    GroupUsageInfo info = new GroupUsageInfo(null, 1L, 10L);
    assertEquals(10d, info.getPercent(), 0.001);

    info = new GroupUsageInfo(null, 1L, 0L);
    assertEquals(-1, info.getPercent(), 0.001);

    info = new GroupUsageInfo(null, 1L, 1L);
    assertEquals(100, info.getPercent(), 0.001);
  }
}
