package com.researchspace.maintenance.api.v2;

import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import com.researchspace.model.collection.ParsedDocument;
import java.util.Date;
import java.util.Map;

/** Body accepted when creating a maintenance window. */
public record ApiV2MaintenanceInput(
    Date startDate, Date endDate, Date stopUserLoginDate, String message) {

  public static ApiV2MaintenanceInput from(ParsedDocument document) {
    if (document.operation() != WriteOperation.CREATE) {
      throw new IllegalArgumentException("Maintenance input requires a create document");
    }
    Map<String, Object> values = document.values();
    return new ApiV2MaintenanceInput(
        Date.class.cast(values.get("startDate")),
        Date.class.cast(values.get("endDate")),
        Date.class.cast(values.get("stopUserLoginDate")),
        String.class.cast(values.get("message")));
  }

  public ScheduledMaintenance toScheduledMaintenance() {
    ScheduledMaintenance maintenance = new ScheduledMaintenance(startDate, endDate);
    if (stopUserLoginDate != null) {
      maintenance.setStopUserLoginDate(stopUserLoginDate);
    }
    maintenance.setMessage(message);
    return maintenance;
  }
}
