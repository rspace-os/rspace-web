package com.researchspace.maintenance.service;

import com.researchspace.maintenance.model.ApiV2MaintenanceResource;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.ResourceRequest.Page;
import com.researchspace.model.collection.RsqlFilterParser;
import com.researchspace.model.collection.Sort;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/** Shared fixture builders for the maintenance collection tests. */
final class MaintenanceTestFixtures {

  private MaintenanceTestFixtures() {}

  static FilterExpression filter(String where) {
    return new RsqlFilterParser(ApiV2MaintenanceResource.DESCRIPTION).parse(where);
  }

  static ResourceRequest pagedRequest(String where, List<Sort> sort, int page, int pageSize) {
    return new ResourceRequest(
        filter(where), sort, new Page(page, pageSize), FieldSelection.all(), IncludeTree.empty());
  }

  static ScheduledMaintenance maintenance(int startHours, int endHours) {
    return maintenance(startHours, endHours, null);
  }

  static ScheduledMaintenance maintenance(int startHours, int endHours, String message) {
    Instant now = Instant.now();
    ScheduledMaintenance maintenance =
        new ScheduledMaintenance(
            Date.from(now.plus(Duration.ofHours(startHours))),
            Date.from(now.plus(Duration.ofHours(endHours))));
    maintenance.setMessage(message);
    return maintenance;
  }

  static Date hoursFromNow(int hours) {
    return Date.from(Instant.now().plus(Duration.ofHours(hours)));
  }
}
