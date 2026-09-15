package com.researchspace.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class GroupUsageInfoTest {

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
