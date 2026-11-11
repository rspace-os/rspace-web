package com.researchspace.api.v2.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.api.v2.auth.ApiV2AuthenticationException;
import com.researchspace.api.v2.controller.ApiV2AuditUnavailableException;
import com.researchspace.api.v2.model.ApiV2AuditEvent;
import com.researchspace.api.v2.model.ApiV2AuditQuery;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditData;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailIdentifier;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.HistoricData;
import com.researchspace.model.audittrail.HistoricalEvent;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.ApiV2ResourceDefinition;
import com.researchspace.model.collection.ApiV2ResourceField;
import com.researchspace.model.collection.ApiV2ResourceField.AccessPreset;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionMutationLimits;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.service.UserManager;
import com.researchspace.service.audit.search.AuditTrailActorVisibility;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import com.researchspace.service.resourceaccess.ProtectedResourceAccess;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import jakarta.ws.rs.NotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
        ApiV2ResourceSpec.builder(ThingResource.DESCRIPTION, operations, Long::valueOf)
            .mutationErrors("create-error", "update-error")
            .audit(ResourceAuditPolicy.standard(operations))
            .build();
    resource = new ApiV2ResourceCatalog(List.of(spec)).find("things").orElseThrow();
    auditLog = new ApiV2AuditLog(strictSearch, Clock.fixed(NOW, ZoneOffset.UTC), 100);
    when(operations.findById(7L, actor))
        .thenReturn(Optional.of(new AuditedThing(7L, "Visible", "Hidden")));
  }

  @Test
  void successfulSearchIsAuditedAndFailuresAreNot() {
    var service = mock(AuditTrailService.class);
    auditLog =
        new ApiV2AuditLog(strictSearch, Clock.fixed(NOW, ZoneOffset.UTC), 100, null, service);
    stubResults(List.of());
    auditLog.search(resource, "7", rangeQuery(), actor);
    var captured = ArgumentCaptor.forClass(HistoricalEvent.class);
    verify(service).notify(captured.capture());
    assertEquals(AuditAction.SEARCH, captured.getValue().getAuditAction());
    assertEquals(actor, captured.getValue().getSubject());
    clearInvocations(service);
    var invalid = rangeQuery();
    invalid.setStart(Date.from(NOW.plus(Duration.ofDays(1))));
    assertThrows(
        ApiV2ResourceException.class, () -> auditLog.search(resource, "7", invalid, actor));
    assertThrows(
        ApiV2AuthenticationException.class,
        () -> auditLog.search(resource, "7", rangeQuery(), null));
    verifyNoInteractions(service);
  }

  @Test
  void omitsDescriptions() {
    var result = result("Visible", Instant.parse("2026-01-02T12:00:00Z"));
    result.getEvent().setDescription("Hidden container IC9");
    stubResults(List.of(result));
    var page = auditLog.search(resource, "7", rangeQuery(), actor);
    assertNull(page.docs().get(0).description());
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
    stubResults(Collections.nCopies(21, result));

    ApiV2AuditQuery query = new ApiV2AuditQuery();
    query.setStart(Date.from(Instant.parse("2025-12-01T00:00:00Z")));
    query.setEnd(Date.from(Instant.parse("2026-01-02T23:59:59.999Z")));
    query.setActions(new HashSet<>(Set.of(AuditAction.WRITE)));
    query.setQ("PayloadMarker");

    var page = auditLog.search(resource, "7", query, actor);

    assertEquals(21, page.docs().size());
    assertEquals(Map.of("name", "Visible"), page.docs().get(0).payload());
    assertEquals(eventTime.toString(), page.docs().get(0).timestamp());
    ArgumentCaptor<ApiV2AuditStrictSearch.Request> request =
        ArgumentCaptor.forClass(ApiV2AuditStrictSearch.Request.class);
    verify(strictSearch).search(request.capture(), any(), any());
    query.getActions().clear();
    assertEquals("things:7", request.getValue().oid());
    assertEquals(Set.of(AuditDomain.RECORD), request.getValue().domains());
    assertEquals(Set.of(AuditAction.WRITE), request.getValue().actions());
    assertEquals("payloadmarker", request.getValue().search());
    assertEquals(Set.of("id", "name"), request.getValue().searchableFields());
    assertTrue(request.getValue().relatedIdentifierFields().isEmpty());
    assertEquals(Instant.parse("2025-12-01T00:00:00Z"), request.getValue().fromInclusive());
    assertEquals(Instant.parse("2026-01-03T00:00:00Z"), request.getValue().toExclusive());
  }

  @Test
  void relatedEventsRequireAnExplicitTopLevelIdentifierField(@TempDir Path logs) throws Exception {
    var policy =
        new ResourceAuditPolicy<AuditedThing, Long>(
            operations::findById,
            (entity, subject) -> {},
            entity -> Map.of(),
            entity -> null,
            Set.of("parentId", "purpose"),
            Set.of("parentId"),
            true);
    var spec =
        ApiV2ResourceSpec.builder(ThingResource.DESCRIPTION, operations, Long::valueOf)
            .mutationErrors("create-error", "update-error")
            .audit(policy)
            .build();
    resource = new ApiV2ResourceCatalog(List.of(spec)).find("things").orElseThrow();
    String timestamp =
        DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm:ss,SSS", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse("2026-01-02T12:00:00Z"));
    String prefix = timestamp + " - domain:RECORD action:WRITE [{\"data\":{";
    String suffix = "}}] outside-user(Outside User)\n";
    Files.writeString(
        logs.resolve("RSLogs.txt"),
        prefix
            + "\"id\":\"children:1\",\"parentId\":\"things:8\",\"purpose\":\"things:7\""
            + suffix
            + prefix
            + "\"id\":\"children:2\",\"parentId\":\"things:7\",\"purpose\":\"Related\""
            + suffix
            + prefix
            + "\"id\":\"children:3\",\"parentId\":{\"id\":\"things:7\"}"
            + suffix
            + prefix
            + "\"id\":\"things:7\",\"name\":\"Direct\""
            + suffix);
    ApiV2AuditLog realAuditLog =
        new ApiV2AuditLog(
            new ApiV2AuditStrictSearch(
                logs.toString(), new AuditTrailActorVisibility(mock(UserManager.class))),
            Clock.fixed(NOW, ZoneOffset.UTC),
            100);

    var page = realAuditLog.search(resource, "7", rangeQuery(), actor);

    assertEquals(2, page.docs().size());
    assertEquals(
        Set.of("children:2", "things:7"),
        page.docs().stream().map(ApiV2AuditEvent::target).collect(Collectors.toSet()));
    assertTrue(
        page.docs().stream().anyMatch(event -> "Related".equals(event.payload().get("purpose"))));
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
  void selectsLatestCompletedUtcDayAndExcludesItsNextMidnight() {
    stubResults(List.of());
    ApiV2AuditQuery query = new ApiV2AuditQuery();
    query.setStart(Date.from(Instant.parse("2026-01-01T00:00:00Z")));
    query.setEnd(Date.from(Instant.parse("2026-01-03T11:00:00Z")));

    var page = auditLog.search(resource, "7", query, actor);

    ArgumentCaptor<ApiV2AuditStrictSearch.Request> request =
        ArgumentCaptor.forClass(ApiV2AuditStrictSearch.Request.class);
    verify(strictSearch).search(request.capture(), any(), any());
    assertEquals("2026-01-03T00:00:00Z", page.endExclusive());
    assertEquals(Instant.parse("2026-01-03T00:00:00Z"), request.getValue().toExclusive());
  }

  @ParameterizedTest
  @CsvSource({
    "2026-01-02T23:59:59Z, 2026-01-02, 2026-01-03T00:00:00Z",
    "2026-01-02T23:59:59.999Z, 2026-01-02, 2026-01-03T00:00:00Z",
    "2026-01-02T23:59:58Z, 2026-01-01, 2026-01-02T00:00:00Z"
  })
  void inclusiveEndSelectsCompletedDaysAtSecondPrecision(
      String end, String snapshotDate, String toExclusive) {
    stubResults(List.of());
    ApiV2AuditQuery query = new ApiV2AuditQuery();
    query.setStart(Date.from(Instant.parse("2026-01-01T00:00:00Z")));
    query.setEnd(Date.from(Instant.parse(end)));

    var page = auditLog.search(resource, "7", query, actor);

    ArgumentCaptor<ApiV2AuditStrictSearch.Request> request =
        ArgumentCaptor.forClass(ApiV2AuditStrictSearch.Request.class);
    verify(strictSearch).search(request.capture(), any(), any());
    assertEquals(toExclusive, page.endExclusive());
    assertEquals(Instant.parse(toExclusive), request.getValue().toExclusive());
  }

  @Test
  void historicalInclusiveToSelectsOnlyItsLatestCompletedDay() {
    stubResults(List.of());
    ApiV2AuditQuery query = new ApiV2AuditQuery();
    query.setStart(Date.from(Instant.parse("2025-12-01T00:00:00Z")));
    query.setEnd(Date.from(Instant.parse("2025-12-20T18:00:00Z")));

    var page = auditLog.search(resource, "7", query, actor);

    assertEquals("2025-12-20T00:00:00Z", page.endExclusive());
  }

  @Test
  void todayOnlyRangeIsAValidEmptySnapshot() {
    ApiV2AuditQuery query = new ApiV2AuditQuery();
    query.setStart(Date.from(Instant.parse("2026-01-03T00:00:00Z")));
    query.setEnd(Date.from(Instant.parse("2026-01-03T11:00:00Z")));

    var page = auditLog.search(resource, "7", query, actor);

    assertEquals("2026-01-03T00:00:00Z", page.endExclusive());
    assertTrue(page.docs().isEmpty());
    assertEquals(0, page.docs().size());
    verify(strictSearch, never()).search(any(), any(), any());
  }

  @Test
  void returnsTheCapAndTranslatesStrictFailures() {
    ApiV2AuditLog ceilingTwo = new ApiV2AuditLog(strictSearch, Clock.fixed(NOW, ZoneOffset.UTC), 2);
    var result = result("Visible", Instant.parse("2026-01-02T12:00:00Z"));
    stubResults(List.of(result, result), true);
    var capped = ceilingTwo.search(resource, "7", rangeQuery(), actor);
    ArgumentCaptor<ApiV2AuditStrictSearch.Request> request =
        ArgumentCaptor.forClass(ApiV2AuditStrictSearch.Request.class);
    verify(strictSearch).search(request.capture(), any(), any());
    assertEquals(2, request.getValue().ceiling());
    assertEquals(2, capped.docs().size());
    assertEquals(2, capped.limit());
    assertTrue(capped.truncated());
    doThrow(new ApiV2AuditStrictSearch.StrictReadException("RSLogs.txt", 2, null))
        .when(strictSearch)
        .search(any(), any(), any());
    assertThrows(
        ApiV2AuditUnavailableException.class,
        () -> ceilingTwo.search(resource, "7", rangeQuery(), actor));
  }

  @Test
  void requiresAuthenticationBeforeLookingUpTheResource() {
    assertThrows(
        ApiV2AuthenticationException.class,
        () -> auditLog.search(resource, "7", new ApiV2AuditQuery(), null));

    verify(operations, never()).findById(any(), any());
    verify(strictSearch, never()).search(any(), any(), any());
  }

  @Test
  void hidesAuditLogWhenResourceIsNotReadable() {
    when(operations.findById(7L, actor)).thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class,
        () -> auditLog.search(resource, "7", new ApiV2AuditQuery(), actor));

    verify(strictSearch, never()).search(any(), any(), any());
  }

  @Test
  void auditUsesInheritedResolutionWithoutLoadingThePersistedAcl() {
    ProtectedResourceAccess<AuditedThing, Long> protectedResource =
        mock(ProtectedResourceAccess.class);
    ResourceOperations<AuditedThing, Long> protectedOperations = operationsMock();
    AuditedThing thing = new AuditedThing(7L, "Visible", "Hidden");
    when(protectedOperations.findById(7L, actor)).thenReturn(Optional.of(thing));
    when(protectedResource.isInherited(thing)).thenReturn(true);
    when(protectedResource.viewAuditCapability()).thenReturn("VIEW_AUDIT");
    when(protectedResource.resolveInherited(thing, actor))
        .thenReturn(
            new ResolvedResourceAccess(
                Optional.of("BOOKER"), Set.of("READ_RESOURCE", "VIEW_AUDIT"), List.of()));
    stubResults(List.of());
    ApiV2ResourceSpec<AuditedThing, Long> spec =
        ApiV2ResourceSpec.builder(ThingResource.DESCRIPTION, protectedOperations, Long::valueOf)
            .mutationErrors("create-error", "update-error")
            .expose(Set.of(ResourceOperation.READ))
            .mutationLimits(CollectionMutationLimits.DEFAULT)
            .resourceAccess(new ResourceAccessSpec<>(protectedResource))
            .audit(ResourceAuditPolicy.standard(protectedOperations))
            .build();
    ApiV2ResourceRegistration<?, ?> protectedRegistration =
        new ApiV2ResourceCatalog(List.of(spec)).find("things").orElseThrow();
    ResourceAccessManager accessManager = mock(ResourceAccessManager.class);
    ApiV2AuditLog protectedAuditLog =
        new ApiV2AuditLog(strictSearch, Clock.fixed(NOW, ZoneOffset.UTC), 100, accessManager);

    protectedAuditLog.search(protectedRegistration, "7", rangeQuery(), actor);

    verify(protectedResource, never()).access(thing);
    verify(accessManager, never()).resolve(any(ResourceAccess.class), any());
  }

  @Test
  void refusesAuditWithoutPolicyBeforeEntityLookup() {
    ResourceOperations<PlainThing, Long> plainOperations = operationsMock();
    ApiV2ResourceSpec<PlainThing, Long> spec =
        ApiV2ResourceSpec.builder(PlainResource.DESCRIPTION, plainOperations, Long::valueOf)
            .mutationErrors("create-error", "update-error")
            .build();
    ApiV2ResourceRegistration<?, ?> plainResource =
        new ApiV2ResourceCatalog(List.of(spec)).find("plainThings").orElseThrow();

    assertThrows(
        NotFoundException.class,
        () -> auditLog.search(plainResource, "9", new ApiV2AuditQuery(), actor));
    verify(plainOperations, never()).findById(any(), any());
    verify(plainOperations, never()).find(any(), any());
    verifyNoInteractions(strictSearch);
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
            AuditData.fromJson(JacksonUtil.toJson(Map.of("name", name))),
            "user1");
    return new AuditTrailSearchResult(event, timestamp.toEpochMilli());
  }

  private static ApiV2AuditQuery rangeQuery() {
    ApiV2AuditQuery query = new ApiV2AuditQuery();
    query.setStart(Date.from(Instant.parse("2026-01-01T00:00:00Z")));
    query.setEnd(Date.from(Instant.parse("2026-01-02T23:59:59.999Z")));
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
            List.of(new Sort("id", true)),
            AccessPolicy.authenticated());
  }

  private void stubResults(List<AuditTrailSearchResult> raw) {
    stubResults(raw, false);
  }

  private void stubResults(List<AuditTrailSearchResult> raw, boolean truncated) {
    when(strictSearch.search(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              Function<AuditTrailSearchResult, ApiV2AuditEvent> project = invocation.getArgument(2);
              return new ApiV2AuditStrictSearch.ScanResult(
                  raw.stream().map(project).toList(), truncated);
            });
  }
}
