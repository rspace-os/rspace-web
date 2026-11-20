package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.api.v1.model.ApiUiNavigationData.ApiUiNavigationScheduledMaintenance;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import java.util.Date;
import org.junit.jupiter.api.Test;

class ApiUiNavigationDataTest {

  @Test
  void scheduledMaintenanceAllowsANullLoginStopDate() {
    ScheduledMaintenance maintenance = new ScheduledMaintenance(new Date(1_000L), new Date(2_000L));
    maintenance.setStopUserLoginDate(null);

    ApiUiNavigationScheduledMaintenance navigationMaintenance =
        new ApiUiNavigationScheduledMaintenance(maintenance);

    assertEquals(1_000L, navigationMaintenance.getStartDate());
    assertEquals(2_000L, navigationMaintenance.getEndDate());
    assertNull(navigationMaintenance.getStopUserLoginDate());
  }
}
