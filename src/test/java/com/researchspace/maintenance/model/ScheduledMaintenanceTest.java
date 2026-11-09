package com.researchspace.maintenance.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;

class ScheduledMaintenanceTest {

  @Test
  void clearedLoginCutoffFallsBackToTheMaintenanceStart() {
    ScheduledMaintenance maintenance =
        new ScheduledMaintenance(
            Date.from(Instant.parse("2099-01-01T10:00:00Z")),
            Date.from(Instant.parse("2099-01-01T12:00:00Z")));
    maintenance.setStopUserLoginDate(null);

    assertTrue(maintenance.getCanUserLoginNow());
    assertEquals(maintenance.getFormattedStartDate(), maintenance.getFormattedStopUserLoginDate());
  }
}
