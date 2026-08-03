package com.researchspace.maintenance.model;

import static com.researchspace.model.collection.ApiV2ResourceField.Access.READ_ONLY;

import com.researchspace.model.User;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.ApiV2ResourceDefinition;
import com.researchspace.model.collection.ApiV2ResourceField;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Sort;
import java.util.Date;
import java.util.List;

@ApiV2ResourceDefinition(name = "maintenances", entity = ScheduledMaintenance.class, id = "id")
public record ApiV2MaintenanceResource(
    @ApiV2ResourceField(
            access = READ_ONLY,
            description = "Stable maintenance identifier.",
            example = "42",
            additionalExamples = {"43"})
        Long id,
    @ApiV2ResourceField(
            requiredOnCreate = true,
            description = "Beginning of the maintenance window.",
            example = "2026-08-01T20:00:00Z")
        Date startDate,
    @ApiV2ResourceField(
            requiredOnCreate = true,
            description = "End of the maintenance window.",
            example = "2026-08-01T21:00:00Z")
        Date endDate,
    @ApiV2ResourceField(
            nullable = true,
            description = "Time after which ordinary users may no longer log in.")
        Date stopUserLoginDate,
    @ApiV2ResourceField(
            nullable = true,
            maxLength = User.DEFAULT_MAXFIELD_LEN,
            description = "User-facing maintenance message.")
        String message) {

  public static final CollectionDescription<ScheduledMaintenance> DESCRIPTION =
      CollectionDescription.fromApiV2Resource(
          ApiV2MaintenanceResource.class,
          List.of(),
          List.of(new Sort("startDate", true), new Sort("id", true)),
          AccessPolicy.publicReadsSysadminWrites());
}
