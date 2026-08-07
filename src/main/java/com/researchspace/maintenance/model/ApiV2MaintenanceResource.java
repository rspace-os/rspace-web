package com.researchspace.maintenance.model;

import com.researchspace.model.User;
import com.researchspace.model.collection.AccessDocumentation;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.ApiV2ResourceDefinition;
import com.researchspace.model.collection.ApiV2ResourceField;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.FilterExpression;
import java.util.Date;
import java.util.List;
import java.util.Set;

@ApiV2ResourceDefinition(
    name = "maintenances",
    entity = ScheduledMaintenance.class,
    id = "id",
    auditFields = false)
public record ApiV2MaintenanceResource(
    @ApiV2ResourceField(
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
        String message,
    @ApiV2ResourceField(
            filterable = false,
            sortable = false,
            description = "Whether ordinary users can log in at the current server time.")
        boolean canUserLoginNow) {

  /**
   * Authenticated callers see all rows. Anonymous callers see only windows that have not ended;
   * deletion is physical, so deleted rows are absent from the collection by construction.
   */
  private static final AccessFunction READ_ACCESS =
      AccessFunction.documented(
          new AccessDocumentation(
              "Anyone may read current and future maintenance; authenticated callers may also read "
                  + "past maintenance.",
              Set.of(),
              AccessDocumentation.AuthenticationRequirement.PUBLIC),
          context ->
              context.isAuthenticated()
                  ? AccessResult.allowed()
                  : AccessResult.allowedWhere(
                      new FilterExpression.Comparison(
                          "endDate", Operator.GREATER_THAN, List.of(new Date()), false)));

  private static final AccessPolicy ACCESS =
      new AccessPolicy(
          READ_ACCESS,
          AccessFunction.sysadmin(),
          AccessFunction.sysadmin(),
          AccessFunction.sysadmin(),
          AccessFunction.sysadmin());

  public static final CollectionDescription<ScheduledMaintenance> DESCRIPTION =
      CollectionDescription.fromApiV2Resource(
          ApiV2MaintenanceResource.class,
          ScheduledMaintenance.class,
          List.of(),
          List.of(new Sort("startDate", true), new Sort("id", true)),
          ACCESS);
}
