package com.researchspace.api.v2.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v2.auth.ApiV2AuthenticationException;
import com.researchspace.api.v2.controller.ApiV2AuditSnapshotConflictException;
import com.researchspace.api.v2.controller.ApiV2AuditUnavailableException;
import com.researchspace.api.v2.controller.ApiV2BadRequestException;
import com.researchspace.api.v2.model.ApiV2AuditEvent;
import com.researchspace.api.v2.model.ApiV2AuditQuery;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditData;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailIdentifier;
import com.researchspace.model.audittrail.HistoricData;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.ApiV2ResourceDefinition;
import com.researchspace.model.collection.ApiV2ResourceField;
import com.researchspace.model.collection.ApiV2ResourceField.AccessPreset;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import jakarta.ws.rs.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApiV2AuditLogTest {

  private static final Instant NOW = Instant.parse("2026-01-03T12:00:00Z");

  private final ApiV2AuditStrictSearch strictSearch = mock(ApiV2AuditStrictSearch.class);
  private final User actor = mock(User.class);
  private final ResourceOperations<AuditedThing, Long> operations = operations();
  private ApiV2ResourceRegistration<?, ?> resource;
  private ApiV2AuditLog auditLog;

  @BeforeEach
  void setUp() {
    ApiV2ResourceSpec<AuditedThing, Long> spec =
        new ApiV2ResourceSpec<>(
            ThingResource.DESCRIPTION, operations, Long::valueOf, "create-error", "update-error");
    resource = new ApiV2ResourceCatalog(List.of(spec)).find("things").orElseThrow();
    auditLog = new ApiV2AuditLog(strictSearch, Clock.fixed(NOW, ZoneOffset.UTC), 100);
    when(operations.find(any(), eq(actor)))
        .thenReturn(new ResourcePage<>(List.of(new AuditedThing(7L, "Visible", "Hidden")), 1));
  }

  @Test
  void searchesByResourceAuditIdentityAndRemovesPrivatePayload() {
    AuditData data =
        AuditData.fromJson(
            "{\"id\":\"things:7\",\"name\":\"Visible\",\"secret\":\"Hidden\","
                + "\"internal\":\"Private\"}");
    HistoricData event =
        new HistoricData(AuditDomain.RECORD, AuditAction.WRITE, "A User", data, "user1");
    Instant eventTime = Instant.parse("2026-01-02T12:00:00Z");
    AuditTrailSearchResult result = new AuditTrailSearchResult(event, eventTime.toEpochMilli());
    when(strictSearch.search(any())).thenReturn(Collections.nCopies(21, result));

    ApiV2AuditQuery query = new ApiV2AuditQuery();
    query.setPage(2);
    query.setLimit(20);
    query.setDateFrom(Date.from(Instant.parse("2025-12-01T00:00:00Z")));
    query.setDateTo(Date.from(Instant.parse("2026-01-02T23:59:59.999Z")));
    query.setActions(new HashSet<>(Set.of(AuditAction.WRITE)));

    var page = auditLog.search(resource, "7", query, actor);

    assertEquals(21, page.totalDocs());
    assertEquals(2, page.page());
    assertEquals(Map.of("name", "Visible"), page.docs().get(0).payload());
    assertEquals(eventTime.toString(), page.docs().get(0).timestamp());
    ArgumentCaptor<ApiV2AuditStrictSearch.Request> request =
        ArgumentCaptor.forClass(ApiV2AuditStrictSearch.Request.class);
    verify(strictSearch).search(request.capture());
    query.getActions().clear();
    assertEquals("things:7", request.getValue().oid());
    assertEquals(Set.of(AuditDomain.RECORD), request.getValue().domains());
    assertEquals(Set.of(AuditAction.WRITE), request.getValue().actions());
    assertEquals(Instant.parse("2025-12-01T00:00:00Z"), request.getValue().fromInclusive());
    assertEquals(Instant.parse("2026-01-03T00:00:00Z"), request.getValue().toExclusive());
  }

  @Test
  void canonicalIdentitySortsMapKeysRecursivelyAndKeepsExplicitNulls() {
    Map<String, Object> firstNested = new LinkedHashMap<>();
    firstNested.put("z", null);
    firstNested.put("a", List.of(2, 1));
    Map<String, Object> secondNested = new LinkedHashMap<>();
    secondNested.put("a", List.of(2, 1));
    secondNested.put("z", null);
    ApiV2AuditEvent first = auditEvent(Map.of("outer", firstNested, "value", 1.0));
    ApiV2AuditEvent second = auditEvent(Map.of("value", 1, "outer", secondNested));

    assertEquals(ApiV2AuditLog.eventId(first), ApiV2AuditLog.eventId(second));
    assertEquals(
        "2a9be85a611df7a4b3d58fcbe970946c5a9b59f49d89f816d90b58eef02d3c9d",
        ApiV2AuditLog.eventId(first));
  }

  @Test
  void sameMillisecondEventsSortByEventIdAndExactDuplicatesRemain() {
    AuditTrailSearchResult first = result("Alpha", Instant.parse("2026-01-02T12:00:00Z"));
    AuditTrailSearchResult second = result("Beta", Instant.parse("2026-01-02T12:00:00Z"));
    when(strictSearch.search(any())).thenReturn(List.of(second, first, first));
    ApiV2AuditQuery query = rangeQuery();

    var page = auditLog.search(resource, "7", query, actor);

    assertEquals(3, page.docs().size());
    assertTrue(page.docs().get(0).eventId().compareTo(page.docs().get(1).eventId()) <= 0);
    assertTrue(page.docs().get(1).eventId().compareTo(page.docs().get(2).eventId()) <= 0);
    assertEquals(
        2, new HashSet<>(page.docs().stream().map(ApiV2AuditEvent::eventId).toList()).size());
  }

  @Test
  void selectsLatestCompletedUtcDayAndExcludesItsNextMidnight() {
    when(strictSearch.search(any())).thenReturn(List.of());
    ApiV2AuditQuery query = new ApiV2AuditQuery();
    query.setDateFrom(Date.from(Instant.parse("2026-01-01T00:00:00Z")));
    query.setDateTo(Date.from(Instant.parse("2026-01-03T11:00:00Z")));

    var page = auditLog.search(resource, "7", query, actor);

    ArgumentCaptor<ApiV2AuditStrictSearch.Request> request =
        ArgumentCaptor.forClass(ApiV2AuditStrictSearch.Request.class);
    verify(strictSearch).search(request.capture());
    assertEquals("2026-01-02", page.snapshotDate());
    assertEquals(Instant.parse("2026-01-03T00:00:00Z"), request.getValue().toExclusive());
  }

  @Test
  void historicalInclusiveToSelectsOnlyItsLatestCompletedDay() {
    when(strictSearch.search(any())).thenReturn(List.of());
    ApiV2AuditQuery query = new ApiV2AuditQuery();
    query.setDateFrom(Date.from(Instant.parse("2025-12-01T00:00:00Z")));
    query.setDateTo(Date.from(Instant.parse("2025-12-20T18:00:00Z")));

    var page = auditLog.search(resource, "7", query, actor);

    assertEquals("2025-12-19", page.snapshotDate());
  }

  @Test
  void todayOnlyRangeIsAValidEmptySnapshot() {
    ApiV2AuditQuery query = new ApiV2AuditQuery();
    query.setDateFrom(Date.from(Instant.parse("2026-01-03T00:00:00Z")));
    query.setDateTo(Date.from(Instant.parse("2026-01-03T11:00:00Z")));

    var page = auditLog.search(resource, "7", query, actor);

    assertEquals("2026-01-02", page.snapshotDate());
    assertTrue(page.docs().isEmpty());
    assertEquals(0, page.totalDocs());
    verify(strictSearch, never()).search(any());
  }

  @Test
  void laterPageReusesTheSnapshotPairAndDetectsChangedResults() {
    when(strictSearch.search(any()))
        .thenReturn(List.of(result("Visible", Instant.parse("2026-01-02T12:00:00Z"))));
    ApiV2AuditQuery firstQuery = rangeQuery();
    var first = auditLog.search(resource, "7", firstQuery, actor);
    ApiV2AuditQuery laterQuery = rangeQuery();
    laterQuery.setSnapshotDate(first.snapshotDate());
    laterQuery.setSnapshotFingerprint(first.snapshotFingerprint());

    var later = auditLog.search(resource, "7", laterQuery, actor);

    assertEquals(first.snapshotFingerprint(), later.snapshotFingerprint());
    laterQuery.setSnapshotFingerprint("0".repeat(64));
    assertThrows(
        ApiV2AuditSnapshotConflictException.class,
        () -> auditLog.search(resource, "7", laterQuery, actor));
  }

  @Test
  void rejectsMalformedOneSidedAndFutureSnapshots() {
    for (ApiV2AuditQuery query :
        List.of(
            snapshotQuery("2026-01-02", null),
            snapshotQuery(null, "0".repeat(64)),
            snapshotQuery("02-01-2026", "0".repeat(64)),
            snapshotQuery("2026-01-03", "0".repeat(64)),
            snapshotQuery("2026-01-02", "A".repeat(64)))) {
      assertThrows(
          ApiV2BadRequestException.class, () -> auditLog.search(resource, "7", query, actor));
    }
  }

  @Test
  void returnsTooManyOnlyAfterCeilingPlusOneAndTranslatesStrictFailures() {
    ApiV2AuditLog ceilingTwo = new ApiV2AuditLog(strictSearch, Clock.fixed(NOW, ZoneOffset.UTC), 2);
    AuditTrailSearchResult result = result("Visible", Instant.parse("2026-01-02T12:00:00Z"));
    when(strictSearch.search(any())).thenReturn(List.of(result, result));
    assertEquals(2, ceilingTwo.search(resource, "7", rangeQuery(), actor).totalDocs());
    when(strictSearch.search(any())).thenReturn(List.of(result, result, result));
    ApiV2BadRequestException tooMany =
        assertThrows(
            ApiV2BadRequestException.class,
            () -> ceilingTwo.search(resource, "7", rangeQuery(), actor));
    assertEquals("errors.api.v2.audit.results.tooMany", tooMany.getErrorCode());

    when(strictSearch.search(any()))
        .thenThrow(new ApiV2AuditStrictSearch.StrictReadException("RSLogs.txt", 2, null));
    assertThrows(
        ApiV2AuditUnavailableException.class,
        () -> ceilingTwo.search(resource, "7", rangeQuery(), actor));
  }

  @Test
  void requiresAuthenticationBeforeLookingUpTheResource() {
    assertThrows(
        ApiV2AuthenticationException.class,
        () -> auditLog.search(resource, "7", new ApiV2AuditQuery(), null));

    verify(operations, never()).find(any(), any());
    verify(strictSearch, never()).search(any());
  }

  @Test
  void hidesAuditLogWhenResourceIsNotReadable() {
    when(operations.find(any(), eq(actor))).thenReturn(new ResourcePage<>(List.of(), 0));

    assertThrows(
        NotFoundException.class,
        () -> auditLog.search(resource, "7", new ApiV2AuditQuery(), actor));

    verify(strictSearch, never()).search(any());
  }

  @Test
  void returnsEmptyPageWhenEntityDoesNotPublishAuditMetadata() {
    ResourceOperations<PlainThing, Long> plainOperations = operationsMock();
    PlainThing plain = new PlainThing(9L);
    when(plainOperations.find(any(), eq(actor))).thenReturn(new ResourcePage<>(List.of(plain), 1));
    ApiV2ResourceSpec<PlainThing, Long> spec =
        new ApiV2ResourceSpec<>(
            PlainResource.DESCRIPTION,
            plainOperations,
            Long::valueOf,
            "create-error",
            "update-error");
    ApiV2ResourceRegistration<?, ?> plainResource =
        new ApiV2ResourceCatalog(List.of(spec)).find("plainThings").orElseThrow();

    var page = auditLog.search(plainResource, "9", new ApiV2AuditQuery(), actor);

    assertTrue(page.docs().isEmpty());
    assertEquals(0, page.totalDocs());
    verify(strictSearch, never()).search(any());
  }

  private static ResourceOperations<AuditedThing, Long> operations() {
    return operationsMock();
  }

  private static ApiV2AuditEvent auditEvent(Map<String, Object> payload) {
    return new ApiV2AuditEvent(
        null,
        "2026-01-02T12:00:00Z",
        "alice",
        null,
        AuditDomain.RECORD,
        AuditAction.WRITE,
        null,
        payload);
  }

  private static AuditTrailSearchResult result(String name, Instant timestamp) {
    HistoricData event =
        new HistoricData(
            AuditDomain.RECORD,
            AuditAction.WRITE,
            "A User",
            AuditData.fromJson("{\"name\":\"" + name + "\"}"),
            "user1");
    return new AuditTrailSearchResult(event, timestamp.toEpochMilli());
  }

  private static ApiV2AuditQuery rangeQuery() {
    ApiV2AuditQuery query = new ApiV2AuditQuery();
    query.setDateFrom(Date.from(Instant.parse("2026-01-01T00:00:00Z")));
    query.setDateTo(Date.from(Instant.parse("2026-01-02T23:59:59.999Z")));
    return query;
  }

  private static ApiV2AuditQuery snapshotQuery(String date, String fingerprint) {
    ApiV2AuditQuery query = rangeQuery();
    query.setSnapshotDate(date);
    query.setSnapshotFingerprint(fingerprint);
    return query;
  }

  @SuppressWarnings("unchecked") // Mockito creates an erased interface mock; ID use is test-owned.
  private static <T> ResourceOperations<T, Long> operationsMock() {
    return (ResourceOperations<T, Long>) mock(ResourceOperations.class);
  }

  @AuditTrailData(auditDomain = AuditDomain.RECORD)
  public static final class AuditedThing {

    private final Long id;
    private final String name;
    private final String secret;

    AuditedThing(Long id, String name, String secret) {
      this.id = id;
      this.name = name;
      this.secret = secret;
    }

    public Long getId() {
      return id;
    }

    @AuditTrailIdentifier
    public String getAuditIdentifier() {
      return "things:" + id;
    }

    public String getName() {
      return name;
    }

    public String getSecret() {
      return secret;
    }
  }

  @ApiV2ResourceDefinition(
      name = "things",
      entity = AuditedThing.class,
      id = "id",
      auditFields = false)
  record ThingResource(
      @ApiV2ResourceField Long id,
      @ApiV2ResourceField String name,
      @ApiV2ResourceField(readAccess = AccessPreset.NEVER) String secret) {

    static final CollectionDescription<AuditedThing> DESCRIPTION =
        CollectionDescription.fromApiV2Resource(
            ThingResource.class,
            AuditedThing.class,
            List.of(),
            List.of(new Sort("id", true)),
            AccessPolicy.authenticated());
  }

  public static final class PlainThing {

    private final Long id;

    PlainThing(Long id) {
      this.id = id;
    }

    public Long getId() {
      return id;
    }
  }

  @ApiV2ResourceDefinition(
      name = "plainThings",
      entity = PlainThing.class,
      id = "id",
      auditFields = false)
  record PlainResource(@ApiV2ResourceField Long id) {

    static final CollectionDescription<PlainThing> DESCRIPTION =
        CollectionDescription.fromApiV2Resource(
            PlainResource.class,
            PlainThing.class,
            List.of(),
            List.of(new Sort("id", true)),
            AccessPolicy.authenticated());
  }
}
