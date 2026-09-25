package com.researchspace.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

public class GroupUsageInfoTest {

  @Test
  public void testGetPercent() {
    GroupUsageInfo info = new GroupUsageInfo(null, 1L, 10L);
    assertThat(info.getPercent()).isCloseTo(10d, within(0.001));

    info = new GroupUsageInfo(null, 1L, 0L);
    assertThat(info.getPercent()).isCloseTo(-1, within(0.001));

    info = new GroupUsageInfo(null, 1L, 1L);
    assertThat(info.getPercent()).isCloseTo(100, within(0.001));
  }
}
