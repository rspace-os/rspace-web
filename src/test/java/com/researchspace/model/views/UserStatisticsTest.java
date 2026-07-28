package com.researchspace.model.views;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UserStatisticsTest {

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
  public void tearDown() throws Exception {}

  // RSPAC-1200
  @Test
  public void testGetUsedLicenseSeats() {
    UserStatistics stats = new UserStatistics(13, 13, 0, 5);
    stats.setTotalEnabledRSpaceAdmins(1);
    stats.setTotalEnabledSysAdmins(4);
    assertEquals(8, stats.getUsedLicenseSeats());
  }
}
