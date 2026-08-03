package com.researchspace.maintenance.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.resource.ApiV2DocumentParser;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import com.researchspace.model.collection.FilterExpression;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiV2MaintenanceResourceTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void generatesDocumentsWithStableKeyOrder() {
    ScheduledMaintenance maintenance =
        new ScheduledMaintenance(
            Date.from(Instant.parse("2026-08-01T10:00:00Z")),
            Date.from(Instant.parse("2026-08-01T12:00:00Z")));
    maintenance.setMessage("upgrade");

    Map<String, Object> document = ApiV2MaintenanceResource.DESCRIPTION.toDocument(maintenance);

    assertEquals(
        List.of("id", "startDate", "endDate", "stopUserLoginDate", "message"),
        List.copyOf(document.keySet()));
    assertEquals("2026-08-01T10:00:00Z", document.get("startDate"));
    assertEquals("2026-08-01T12:00:00Z", document.get("endDate"));
    assertEquals("upgrade", document.get("message"));
    assertNull(document.get("id"));
  }

  @Test
  void exposesOnlyNonIdFieldsAsWritable() {
    assertEquals(
        List.of("startDate", "endDate", "stopUserLoginDate", "message"),
        List.copyOf(ApiV2MaintenanceResource.DESCRIPTION.writableFields(WriteOperation.UPDATE)));
  }

  @Test
  void derivesWireAndQueryBehaviorFromFieldTypes() {
    assertEquals(
        com.researchspace.model.collection.CollectionFieldType.InputKind.STRING,
        ApiV2MaintenanceResource.DESCRIPTION.requireField("startDate").type().inputKind());
    assertEquals(
        com.researchspace.model.collection.CollectionFieldType.InputKind.NUMBER,
        ApiV2MaintenanceResource.DESCRIPTION.requireField("id").type().inputKind());
    assertTrue(
        ApiV2MaintenanceResource.DESCRIPTION
            .requireField("message")
            .operators()
            .contains(Operator.LIKE));
    assertFalse(
        ApiV2MaintenanceResource.DESCRIPTION
            .requireField("startDate")
            .operators()
            .contains(Operator.LIKE));
  }

  @Test
  void constrainsAnonymousReadsToMaintenanceThatHasNotEnded() {
    User member = mock(User.class);
    User sysadmin = mock(User.class);
    when(sysadmin.hasRole(Role.SYSTEM_ROLE)).thenReturn(true);

    AccessResult.AllowedWhere anonymous =
        assertInstanceOf(
            AccessResult.AllowedWhere.class,
            ApiV2MaintenanceResource.DESCRIPTION
                .accessPolicy()
                .readAccess()
                .check(new AccessContext(null, Operation.READ, "maintenances")));
    FilterExpression.Comparison constraint =
        assertInstanceOf(FilterExpression.Comparison.class, anonymous.constraint());
    assertEquals("endDate", constraint.field());
    assertEquals(Operator.GREATER_THAN, constraint.operator());
    assertInstanceOf(Date.class, constraint.values().get(0));

    assertInstanceOf(
        AccessResult.Allowed.class,
        ApiV2MaintenanceResource.DESCRIPTION
            .accessPolicy()
            .readAccess()
            .check(new AccessContext(member, Operation.READ, "maintenances")));
    assertTrue(
        ApiV2MaintenanceResource.DESCRIPTION
            .accessPolicy()
            .updateAccess()
            .check(new AccessContext(member, Operation.UPDATE, "maintenances", 1L))
            .isDenied());
    assertFalse(
        ApiV2MaintenanceResource.DESCRIPTION
            .accessPolicy()
            .updateAccess()
            .check(new AccessContext(sysadmin, Operation.UPDATE, "maintenances", 1L))
            .isDenied());
  }

  @Test
  void appliesPatchInDescriptionOrderNotRequestOrder() throws Exception {
    ScheduledMaintenance maintenance =
        new ScheduledMaintenance(
            Date.from(Instant.parse("2026-08-01T10:00:00Z")),
            Date.from(Instant.parse("2026-08-01T12:00:00Z")));

    // stopUserLoginDate is sent first; setStartDate resets the cutoff, so a request-ordered apply
    // would silently discard the explicit value.
    ApiV2MaintenanceResource.DESCRIPTION.apply(
        maintenance,
        ApiV2DocumentParser.parse(
            mapper.readTree(
                """
                {
                  "stopUserLoginDate": "2026-08-02T09:00:00Z",
                  "startDate": "2026-08-02T10:00:00Z",
                  "endDate": "2026-08-02T12:00:00Z"
                }
                """),
            ApiV2MaintenanceResource.DESCRIPTION,
            WriteOperation.UPDATE,
            "errors.api.v2.maintenance.patch",
            writeContext(Operation.UPDATE)));

    assertEquals(
        Date.from(Instant.parse("2026-08-02T09:00:00Z")), maintenance.getStopUserLoginDate());
    assertEquals(Date.from(Instant.parse("2026-08-02T10:00:00Z")), maintenance.getStartDate());
  }

  @Test
  void patchCanClearStopUserLoginDateBackToNoCutoff() throws Exception {
    ScheduledMaintenance maintenance =
        new ScheduledMaintenance(
            Date.from(Instant.parse("2026-08-01T10:00:00Z")),
            Date.from(Instant.parse("2026-08-01T12:00:00Z")));
    maintenance.setStopUserLoginDate(Date.from(Instant.parse("2026-08-01T09:45:00Z")));

    ApiV2MaintenanceResource.DESCRIPTION.apply(
        maintenance,
        ApiV2DocumentParser.parse(
            mapper.readTree("{\"stopUserLoginDate\": null}"),
            ApiV2MaintenanceResource.DESCRIPTION,
            WriteOperation.UPDATE,
            "errors.api.v2.maintenance.patch",
            writeContext(Operation.UPDATE)));

    assertNull(maintenance.getStopUserLoginDate());
    assertTrue(maintenance.getCanUserLoginNow());
  }

  /** A context that imposes no field-level write restriction. */
  private static AccessContext writeContext(Operation operation) {
    return new AccessContext(null, operation, "maintenances");
  }
}
