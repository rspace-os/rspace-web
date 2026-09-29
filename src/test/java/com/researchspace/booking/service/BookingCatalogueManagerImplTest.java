package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingCalendarQuery;
import com.researchspace.booking.dao.BookingItemQuery;
import com.researchspace.booking.dao.BookingLocationQuery;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.dao.query.RsqlCollectionQuery;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.QueryConstraint;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.ResourceFieldSelections;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RuntimeFieldSelection;
import com.researchspace.model.collection.Sort;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.inventory.InstrumentReadAccess;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingCatalogueManagerImplTest {

  @Mock private BookingConfigurationManager configurations;
  @Mock private InstrumentDao instruments;
  @Mock private FeatureFlagManager featureFlags;
  @Mock private BookingItemQuery itemQuery;
  @Mock private BookingCalendarQuery calendarQuery;
  @Mock private BookingLocationQuery locationQuery;
  @Mock private InstrumentReadAccess instrumentReadAccess;
  @Mock private TimeSlotBookingDao bookings;
  @Mock private User caller;

  private BookingCatalogueManagerImpl manager;

  @BeforeEach
  void setUp() {
    manager =
        new BookingCatalogueManagerImpl(
            configurations,
            instruments,
            featureFlags,
            itemQuery,
            calendarQuery,
            new BookingLocationFilterManagerImpl(instruments, instrumentReadAccess, locationQuery),
            bookings);
    when(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, caller)).thenReturn(true);
  }

  @Test
  void appliesCapabilityTargetIntersectionBeforeRequestedPage() {
    RsqlCollectionQuery.Predicate accessRestriction =
        new RsqlCollectionQuery.Predicate(
            "EXISTS bookingItemCapability", Map.of("subject", caller));
    when(itemQuery.restriction(caller, true, false, "bookingConfiguration.target"))
        .thenReturn(accessRestriction);
    when(configurations.getConfigurations(
            any(ResourceRequest.class), same(caller), same(accessRestriction)))
        .thenReturn(new ResourcePage<>(List.of(), 2));
    when(instruments.getBookingRelationshipTargets(Set.of())).thenReturn(Map.of());
    when(instruments.getReadableParentLocationSummaries(Set.of(), caller)).thenReturn(Map.of());

    BookingCatalogueManager.Page result =
        manager.search(
            null,
            null,
            ResourceRequest.unpaged(null),
            List.of(),
            List.of(),
            BookingCatalogueManager.Capability.CREATE_BOOKING,
            false,
            3,
            7,
            caller);

    assertEquals(3, result.page());
    assertEquals(7, result.pageSize());
    assertEquals(2, result.total());
    ArgumentCaptor<ResourceRequest> request = ArgumentCaptor.forClass(ResourceRequest.class);
    verify(configurations)
        .getConfigurations(request.capture(), same(caller), same(accessRestriction));
    assertEquals(new ResourceRequest.Page(3, 7), request.getValue().page());
    FilterExpression.And filter =
        assertInstanceOf(FilterExpression.And.class, request.getValue().serverConstraint());
    assertTrue(
        filter.children().stream()
            .filter(FilterExpression.Comparison.class::isInstance)
            .map(FilterExpression.Comparison.class::cast)
            .anyMatch(
                comparison ->
                    comparison.field().equals("enabled")
                        && comparison.operator() == Operator.EQUAL));
  }

  @Test
  void answersATopLevelLocationFilterWithACorrelatedRestrictionBeforeTheConfigurationQuery() {
    RsqlCollectionQuery.Predicate inTwelve =
        new RsqlCollectionQuery.Predicate("EXISTS storedInTwelve", Map.of());
    when(locationQuery.readableParent(
            caller,
            "bookingConfiguration.target",
            Set.of(12L),
            true,
            "bookingConfiguration_target_location0",
            null,
            null))
        .thenReturn(inTwelve);
    when(configurations.getConfigurations(any(ResourceRequest.class), same(caller), same(inTwelve)))
        .thenReturn(new ResourcePage<>(List.of(), 0));

    manager.search(
        null,
        null,
        ResourceRequest.unpaged(location(12L)),
        List.of(),
        List.of(),
        null,
        false,
        1,
        20,
        caller);

    ArgumentCaptor<ResourceRequest> request = ArgumentCaptor.forClass(ResourceRequest.class);
    verify(configurations).getConfigurations(request.capture(), same(caller), same(inTwelve));
    assertEquals(null, request.getValue().filter());
    org.mockito.Mockito.verify(instruments, org.mockito.Mockito.never())
        .findConfiguredInstrumentIdsInReadableParents(any(), any());
  }

  @Test
  void stillListsTheItemsOfALocationInsideADisjunction() {
    when(instruments.findConfiguredInstrumentIdsInReadableParents(Set.of(12L), caller))
        .thenReturn(Set.of(5L));
    when(configurations.getConfigurations(any(ResourceRequest.class), same(caller)))
        .thenReturn(new ResourcePage<>(List.of(), 0));
    FilterExpression none =
        new FilterExpression.Comparison("id", Operator.EQUAL, List.of(0L), false);

    manager.search(
        null,
        null,
        ResourceRequest.unpaged(new FilterExpression.Or(List.of(location(12L), none))),
        List.of(),
        List.of(),
        null,
        false,
        1,
        20,
        caller);

    ArgumentCaptor<ResourceRequest> request = ArgumentCaptor.forClass(ResourceRequest.class);
    verify(configurations).getConfigurations(request.capture(), same(caller));
    assertEquals(
        new FilterExpression.Or(
            List.of(
                new FilterExpression.Comparison("target.value", Operator.IN, List.of(5L), false),
                none)),
        request.getValue().filter());
  }

  private static FilterExpression location(long containerId) {
    return new FilterExpression.Comparison(
        "location",
        Operator.EQUAL,
        List.of(
            new com.researchspace.model.collection.ResourceReference<>(
                "booking-locations", containerId)),
        false);
  }

  @Test
  void searchesLocationsByGlobalIdAndRestoresOnlyTheRequestedOnes() {
    when(instruments.getBookingCatalogueLocations(null, Set.of(7L), 1, 20, caller))
        .thenReturn(
            new ResourcePage<>(
                List.of(
                    new com.researchspace.model.inventory.InstrumentParentLocationSummary(
                        7L,
                        "WB reader",
                        com.researchspace.model.inventory.Container.ContainerType.WORKBENCH)),
                1));

    BookingCatalogueManager.LocationPage byGlobalId =
        manager.searchLocations("be7", List.of(), List.of(), 1, 20, caller);
    BookingCatalogueManager.LocationPage restored =
        manager.searchLocations(null, List.of(), List.of("IC7", "SA9", "bad"), 1, 20, caller);

    assertEquals(
        List.of(new BookingCatalogueManager.Location("BE7", "WB reader")), byGlobalId.items());
    assertEquals(byGlobalId.items(), restored.items());
    assertEquals(
        0,
        manager.searchLocations(null, List.of(), List.of("SA9"), 1, 20, caller).total(),
        "an ID of another kind names no location");
  }

  @Test
  void returnsEmptyPageWithoutQueryWhenNonSysadminHasNoCapableTargets() {
    RsqlCollectionQuery.Predicate noAccess = new RsqlCollectionQuery.Predicate("1 = 0", Map.of());
    when(itemQuery.restriction(caller, true, false, "bookingConfiguration.target"))
        .thenReturn(noAccess);
    when(configurations.getConfigurations(any(ResourceRequest.class), same(caller), same(noAccess)))
        .thenReturn(new ResourcePage<>(List.of(), 0));

    BookingCatalogueManager.Page result =
        manager.search(
            null,
            null,
            ResourceRequest.unpaged(null),
            List.of(),
            List.of(),
            BookingCatalogueManager.Capability.CREATE_BOOKING,
            false,
            4,
            9,
            caller);

    assertEquals(List.of(), result.items());
    assertEquals(4, result.page());
    assertEquals(9, result.pageSize());
    assertEquals(0, result.total());
    assertEquals(List.of(), result.facets().types());
    verify(configurations)
        .getConfigurations(any(ResourceRequest.class), same(caller), same(noAccess));
  }

  @Test
  void preservesCallerFilterConstraintAndRuntimeWhileOwningCatalogueRequestShape() {
    FilterExpression callerFilter =
        new FilterExpression.Or(
            List.of(
                new FilterExpression.Comparison("id", Operator.EQUAL, List.of(7L), false),
                new FilterExpression.Comparison("id", Operator.EQUAL, List.of(8L), false)));
    FilterExpression callerConstraint =
        new FilterExpression.Comparison("id", Operator.GREATER_THAN, List.of(0L), false);
    RuntimeFieldSelection runtime =
        new RuntimeFieldSelection(
            Map.of("target.customFields.SF1", org.mockito.Mockito.mock(ResolvedRuntimeField.class)),
            Set.of());
    ResourceRequest request =
        new ResourceRequest(
            callerFilter,
            callerConstraint,
            List.of(new Sort("id", false)),
            new ResourceRequest.Page(8, 44),
            ResourceFieldSelections.root(FieldSelection.exclude(Set.of("id"))),
            new IncludeTree(Map.of("target", IncludeTree.empty())),
            runtime);
    when(configurations.getConfigurations(any(ResourceRequest.class), same(caller)))
        .thenReturn(new ResourcePage<>(List.of(), 0));
    when(instruments.getBookingRelationshipTargets(Set.of())).thenReturn(Map.of());
    when(instruments.getReadableParentLocationSummaries(Set.of(), caller)).thenReturn(Map.of());

    manager.search(null, null, request, List.of(), List.of(), null, false, 3, 7, caller);

    ArgumentCaptor<ResourceRequest> captured = ArgumentCaptor.forClass(ResourceRequest.class);
    verify(configurations).getConfigurations(captured.capture(), same(caller));
    ResourceRequest catalogueRequest = captured.getValue();
    assertSame(callerFilter, catalogueRequest.filter());
    assertSame(runtime, catalogueRequest.runtime());
    assertEquals(new ResourceRequest.Page(3, 7), catalogueRequest.page());
    assertEquals(List.of(), catalogueRequest.sort());
    assertEquals(FieldSelection.all(), catalogueRequest.fields());
    assertEquals(IncludeTree.empty(), catalogueRequest.includes());

    QueryConstraint.And restrictions =
        assertInstanceOf(QueryConstraint.And.class, catalogueRequest.serverConstraint());
    assertSame(callerConstraint, restrictions.children().get(0));
    assertInstanceOf(FilterExpression.And.class, restrictions.children().get(1));
  }

  @Test
  void appliesCallerOwnershipBeforePaging() {
    RsqlCollectionQuery.Predicate ownership =
        new RsqlCollectionQuery.Predicate("EXISTS ownedBookingItem", Map.of("owner", caller));
    when(itemQuery.ownedBy(caller, "bookingConfiguration.target")).thenReturn(ownership);
    when(configurations.getConfigurations(
            any(ResourceRequest.class), same(caller), same(ownership)))
        .thenReturn(new ResourcePage<>(List.of(), 0));
    when(instruments.getBookingRelationshipTargets(Set.of())).thenReturn(Map.of());
    when(instruments.getReadableParentLocationSummaries(Set.of(), caller)).thenReturn(Map.of());

    manager.search(
        null, null, ResourceRequest.unpaged(null), List.of(), List.of(), null, true, 2, 20, caller);

    verify(configurations)
        .getConfigurations(any(ResourceRequest.class), same(caller), same(ownership));
  }

  @Test
  void pagesOnlyTheRequestedAvailabilityInCatalogueOrderWithItsTotal() {
    stubAvailabilityCatalogue();

    BookingCatalogueManager.Page freeNow =
        manager.search(
            null,
            null,
            ResourceRequest.unpaged(null),
            List.of(),
            List.of(),
            null,
            false,
            BookingCatalogueManager.Availability.AVAILABLE_NOW,
            NINE_UTC,
            1,
            20,
            caller);

    assertEquals(1, freeNow.total());
    assertEquals(List.of("INSTRUMENT"), freeNow.facets().types());
    verify(instruments).getBookingRelationshipTargets(Set.of(101L));
  }

  @Test
  void countsBothAvailabilityCategoriesWithoutRows() {
    stubAvailabilityCatalogue();

    assertEquals(
        new BookingCatalogueManager.AvailabilityCounts(1, 1),
        manager.countAvailability(
            null,
            null,
            ResourceRequest.unpaged(null),
            List.of(),
            List.of(),
            null,
            false,
            NINE_UTC,
            caller));
    verify(bookings)
        .findConfirmedEventIntervals(
            List.of(1L, 2L, 3L),
            java.util.Date.from(NINE_UTC.start()),
            java.util.Date.from(NINE_UTC.end()));
  }

  @Test
  void readsAvailabilityCandidatesInKeysetBatchesAndSlicesTheRequestedPage() {
    int batch = BookingCatalogueManagerImpl.AVAILABILITY_BATCH_SIZE;
    List<BookingConfiguration> first =
        java.util.stream.LongStream.rangeClosed(1, batch).mapToObj(id -> open(id)).toList();
    List<BookingConfiguration> second = List.of(open(batch + 1L), open(batch + 2L));
    when(configurations.getConfigurations(any(ResourceRequest.class), same(caller)))
        .thenAnswer(
            invocation -> {
              long after = afterId(invocation.<ResourceRequest>getArgument(0).serverConstraint());
              return new ResourcePage<>(
                  after == 0 ? first : after == batch ? second : List.of(), 0);
            });

    BookingCatalogueManager.Page page =
        manager.search(
            null,
            null,
            ResourceRequest.unpaged(null),
            List.of(),
            List.of(),
            null,
            false,
            BookingCatalogueManager.Availability.AVAILABLE_NOW,
            NINE_UTC,
            2,
            batch / 2 + 1,
            caller);

    assertEquals(batch + 2, page.total());
    Set<Long> secondPage =
        java.util.stream.LongStream.rangeClosed(batch / 2 + 2, batch + 2)
            .map(id -> id + 100)
            .boxed()
            .collect(java.util.stream.Collectors.toSet());
    verify(instruments).getBookingRelationshipTargets(secondPage);
  }

  private static final BookingCatalogueManager.AvailabilityWindow NINE_UTC =
      new BookingCatalogueManager.AvailabilityWindow(
          java.time.Instant.parse("2026-08-17T00:00:00Z"),
          java.time.Instant.parse("2026-08-18T00:00:00Z"),
          java.time.Instant.parse("2026-08-17T09:00:00Z"));

  /** Item 1 is free now, item 2 is booked now and free later, item 3 is closed on Mondays. */
  private void stubAvailabilityCatalogue() {
    BookingConfiguration closedMonday = open(3);
    closedMonday.setOpenDays(List.of(2, 3, 4, 5, 6, 7));
    when(configurations.getConfigurations(any(ResourceRequest.class), same(caller)))
        .thenReturn(new ResourcePage<>(List.of(open(1), open(2), closedMonday), 3));
    when(bookings.findConfirmedEventIntervals(any(), any(), any()))
        .thenReturn(
            List.of(
                new TimeSlotBookingDao.EventInterval(
                    2L,
                    com.researchspace.model.booking.BookingEventKind.BOOKING,
                    java.util.Date.from(java.time.Instant.parse("2026-08-17T08:00:00Z")),
                    java.util.Date.from(java.time.Instant.parse("2026-08-17T10:00:00Z")))));
  }

  private static BookingConfiguration open(long id) {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setId(id);
    configuration.replaceTarget(
        new com.researchspace.model.booking.BookableTargetReference(
            com.researchspace.model.booking.BookableTargetType.INSTRUMENT, id + 100));
    configuration.setTimeZone("UTC");
    configuration.setOpeningStart("00:00");
    configuration.setOpeningEnd("24:00");
    configuration.setOpenDays(List.of(1, 2, 3, 4, 5, 6, 7));
    configuration.setOpeningExceptions(List.of());
    return configuration;
  }

  /** The keyset lower bound of one availability batch, or 0 when none is present. */
  private static long afterId(QueryConstraint constraint) {
    if (constraint instanceof QueryConstraint.And and) {
      return and.children().stream().mapToLong(child -> afterId(child)).max().orElse(0);
    }
    if (constraint instanceof FilterExpression.And and) {
      return and.children().stream().mapToLong(child -> afterId(child)).max().orElse(0);
    }
    if (constraint instanceof FilterExpression.Comparison comparison
        && comparison.field().equals("id")
        && comparison.operator() == Operator.GREATER_THAN) {
      return ((Number) comparison.values().get(0)).longValue();
    }
    return 0;
  }
}
