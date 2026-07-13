package com.researchspace.api.v2.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import java.util.Date;

public record ApiV2Maintenance(
    Long id,
    @JsonSerialize(using = ISO8601DateTimeSerialiser.class) Long startDate,
    @JsonSerialize(using = ISO8601DateTimeSerialiser.class) Long endDate,
    @JsonSerialize(using = ISO8601DateTimeSerialiser.class) Long stopUserLoginDate,
    String message) {

  public ApiV2Maintenance(ScheduledMaintenance maintenance) {
    this(
        maintenance.getId(),
        toEpochMillis(maintenance.getStartDate()),
        toEpochMillis(maintenance.getEndDate()),
        toEpochMillis(maintenance.getStopUserLoginDate()),
        maintenance.getMessage());
  }

  private static Long toEpochMillis(Date date) {
    return date == null ? null : date.getTime();
  }
}
