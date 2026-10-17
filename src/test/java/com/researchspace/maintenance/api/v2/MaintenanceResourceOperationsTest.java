package com.researchspace.maintenance.api.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.resource.ApiV2DocumentParser;
import com.researchspace.maintenance.model.ApiV2MaintenanceResource;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.WriteOperation;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MaintenanceResourceOperationsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String ERROR_KEY = "errors.api.v2.invalidRequest";

  @ParameterizedTest
  @MethodSource("creationCases")
  void preservesCutoffPresenceAndDelegatesSubject(boolean bulk, CutoffInput cutoffInput) {
    Instant now = Instant.now();
    Instant start = now.plus(5, ChronoUnit.MINUTES);
    Instant end = start.plus(10, ChronoUnit.MINUTES);
    Instant explicitCutoff = start.minus(1, ChronoUnit.MINUTES);

    User sysadmin = mock(User.class);
    when(sysadmin.hasRole(Role.SYSTEM_ROLE)).thenReturn(true);
    User actor = mock(User.class);
    ApiV2Caller caller = new ApiV2Caller(sysadmin, actor);
    AccessContext context = new AccessContext(sysadmin, Operation.CREATE, "maintenances");
    ObjectNode input = maintenanceInput(start, end, explicitCutoff, cutoffInput);

    MaintenanceManager manager = mock(MaintenanceManager.class);
    MaintenanceResourceOperations operations = new MaintenanceResourceOperations(manager);
    ScheduledMaintenance created;
    if (bulk) {
      ArrayNode docs = MAPPER.createArrayNode();
      docs.add(input);
      ObjectNode body = MAPPER.createObjectNode();
      body.set("docs", docs);
      List<ParsedDocument> documents =
          ApiV2DocumentParser.parseMany(
              body, ApiV2MaintenanceResource.DESCRIPTION, ERROR_KEY, context);
      when(manager.createResources(anyList(), same(sysadmin)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      List<ScheduledMaintenance> returned = operations.createMany(documents, caller);

      verify(manager).createResources(anyList(), same(sysadmin));
      assertEquals(1, returned.size());
      created = returned.get(0);
    } else {
      ParsedDocument document =
          ApiV2DocumentParser.parse(
              input,
              ApiV2MaintenanceResource.DESCRIPTION,
              WriteOperation.CREATE,
              ERROR_KEY,
              context);
      when(manager.createResource(any(ScheduledMaintenance.class), same(sysadmin)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      created = operations.create(document, caller);

      verify(manager).createResource(any(ScheduledMaintenance.class), same(sysadmin));
    }

    assertEquals(Date.from(start), created.getStartDate());
    assertEquals(Date.from(end), created.getEndDate());
    switch (cutoffInput) {
      case OMITTED -> {
        assertEquals(
            Date.from(start.minus(10, ChronoUnit.MINUTES)), created.getStopUserLoginDate());
        assertFalse(created.getCanUserLoginNow());
      }
      case EXPLICIT_NULL -> {
        assertNull(created.getStopUserLoginDate());
        assertTrue(created.getCanUserLoginNow());
      }
      case EXPLICIT_VALUE -> {
        assertEquals(Date.from(explicitCutoff), created.getStopUserLoginDate());
        assertTrue(created.getCanUserLoginNow());
      }
    }
    assertEquals("Planned maintenance", created.getMessage());
  }

  private static ObjectNode maintenanceInput(
      Instant start, Instant end, Instant explicitCutoff, CutoffInput cutoffInput) {
    ObjectNode input = MAPPER.createObjectNode();
    input.put("startDate", start.toString());
    input.put("endDate", end.toString());
    input.put("message", "Planned maintenance");
    if (cutoffInput == CutoffInput.EXPLICIT_NULL) {
      input.putNull("stopUserLoginDate");
    } else if (cutoffInput == CutoffInput.EXPLICIT_VALUE) {
      input.put("stopUserLoginDate", explicitCutoff.toString());
    }
    return input;
  }

  private static Stream<Arguments> creationCases() {
    return Stream.of(
        Arguments.of(false, CutoffInput.OMITTED),
        Arguments.of(false, CutoffInput.EXPLICIT_NULL),
        Arguments.of(false, CutoffInput.EXPLICIT_VALUE),
        Arguments.of(true, CutoffInput.OMITTED),
        Arguments.of(true, CutoffInput.EXPLICIT_NULL),
        Arguments.of(true, CutoffInput.EXPLICIT_VALUE));
  }

  private enum CutoffInput {
    OMITTED,
    EXPLICIT_NULL,
    EXPLICIT_VALUE
  }
}
