package com.researchspace.api.v2.model;

import com.researchspace.maintenance.model.ScheduledMaintenance;
import java.util.Date;

public record ApiV2Maintenance(
    Long id, String startDate, String endDate, String stopUserLoginDate, String message) {

  public ApiV2Maintenance(ScheduledMaintenance maintenance) {
    this(
        maintenance.getId(),
        toIsoInstant(maintenance.getStartDate()),
        toIsoInstant(maintenance.getEndDate()),
        toIsoInstant(maintenance.getStopUserLoginDate()),
        maintenance.getMessage());
  }

  private static String toIsoInstant(Date date) {
    return date == null ? null : date.toInstant().toString();
  }
}
