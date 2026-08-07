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
import com.researchspace.api.v2.model.ApiV2AuditQuery;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.model.PaginationCriteria;
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
import com.researchspace.service.audit.search.AuditTrailHandler;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import com.researchspace.service.audit.search.IAuditTrailSearchConfig;
import jakarta.ws.rs.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApiV2AuditLogTest {

  private final AuditTrailHandler handler = mock(AuditTrailHandler.class);
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
    auditLog = new ApiV2AuditLog(handler);
  }

  @Test
  void searchesByResourceAuditIdentityAndRemovesPrivatePayload() {
    AuditedThing thing = new AuditedThing(7L, "Visible", "Hidden");
    when(operations.findById(7L, actor)).thenReturn(Optional.of(thing));
    AuditData data =
        AuditData.fromJson(
            "{\"id\":\"things:7\",\"name\":\"Visible\",\"secret\":\"Hidden\","
                + "\"internal\":\"Private\"}");
    HistoricData event =
        new HistoricData(AuditDomain.RECORD, AuditAction.WRITE, "A User", data, "user1");
    Instant eventTime =
        Instant.ofEpochMilli(Instant.now().minus(Duration.ofDays(1)).toEpochMilli());
    AuditTrailSearchResult result = new AuditTrailSearchResult(event, eventTime.toEpochMilli());
    when(handler.searchAuditTrail(any(), any(), eq(actor)))
        .thenReturn(new SearchResultsImpl<>(List.of(result), 1, 21, 20));

    ApiV2AuditQuery query = new ApiV2AuditQuery();
    query.setPage(2);
    query.setLimit(20);
    query.setDateFrom(Date.from(Instant.now().minus(Duration.ofDays(500))));
    query.setDateTo(new Date());
    query.setActions(new HashSet<>(Set.of(AuditAction.WRITE)));

    var page = auditLog.search(resource, "7", query, actor);

    assertEquals(21, page.totalDocs());
    assertEquals(2, page.page());
    assertEquals(Map.of("name", "Visible"), page.docs().get(0).payload());
    assertEquals(eventTime.toString(), page.docs().get(0).timestamp());
    ArgumentCaptor<IAuditTrailSearchConfig> config =
        ArgumentCaptor.forClass(IAuditTrailSearchConfig.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<PaginationCriteria<AuditTrailSearchResult>> pagination =
        ArgumentCaptor.forClass(PaginationCriteria.class);
    verify(handler).searchAuditTrail(config.capture(), pagination.capture(), eq(actor));
    Date restrictedFrom = config.getValue().getDateFrom();
    Date restrictedTo = config.getValue().getDateTo();
    query.getDateFrom().setTime(0);
    query.getDateTo().setTime(0);
    query.getActions().clear();
    assertEquals("things:7", config.getValue().getOid());
    assertEquals(Set.of(AuditDomain.RECORD), config.getValue().getDomains());
    assertEquals(Set.of(AuditAction.WRITE), config.getValue().getActions());
    assertEquals(restrictedFrom, config.getValue().getDateFrom());
    assertEquals(restrictedTo, config.getValue().getDateTo());
    assertTrue(
        Duration.between(
                    config.getValue().getDateFrom().toInstant(),
                    config.getValue().getDateTo().toInstant())
                .toDays()
            <= ApiV2AuditLog.MAX_SEARCH_RANGE.toDays());
    assertEquals(1L, pagination.getValue().getPageNumber());
    assertEquals(20, pagination.getValue().getResultsPerPage());
  }

  @Test
  void requiresAuthenticationBeforeLookingUpTheResource() {
    assertThrows(
        ApiV2AuthenticationException.class,
        () -> auditLog.search(resource, "7", new ApiV2AuditQuery(), null));

    verify(operations, never()).findById(any(), any());
    verify(handler, never()).searchAuditTrail(any(), any(), any());
  }

  @Test
  void hidesAuditLogWhenResourceIsNotReadable() {
    when(operations.findById(7L, actor)).thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class,
        () -> auditLog.search(resource, "7", new ApiV2AuditQuery(), actor));

    verify(handler, never()).searchAuditTrail(any(), any(), any());
  }

  @Test
  void returnsEmptyPageWhenEntityDoesNotPublishAuditMetadata() {
    @SuppressWarnings("unchecked")
    ResourceOperations<PlainThing, Long> plainOperations = mock(ResourceOperations.class);
    PlainThing plain = new PlainThing(9L);
    when(plainOperations.findById(9L, actor)).thenReturn(Optional.of(plain));
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
    verify(handler, never()).searchAuditTrail(any(), any(), any());
  }

  @SuppressWarnings("unchecked")
  private static ResourceOperations<AuditedThing, Long> operations() {
    return mock(ResourceOperations.class);
  }

  @AuditTrailData(auditDomain = AuditDomain.RECORD)
  static final class AuditedThing {

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
            List.of(),
            List.of(new Sort("id", true)),
            AccessPolicy.authenticated());
  }

  static final class PlainThing {

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
            List.of(),
            List.of(new Sort("id", true)),
            AccessPolicy.authenticated());
  }
}
