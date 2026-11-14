package com.researchspace.api.v2.contract;

import static com.researchspace.api.v2.contract.ApiV2FilterExpansionMVCIT.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.api.v2.contract.ApiV2FilterExpansionMVCIT.CreatedInstrument;
import com.researchspace.api.v2.query.ApiV2ResourceRequestParser;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.booking.dao.BookingCalendarQuery;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.dao.query.RsqlCollectionQuery;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.model.booking.BookingRequesterFilters;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.Sort;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/** Database-backed contract for calendar requester visibility and filters. */
@ApiV2WebIntegrationTest
@DisplayName("REST API v2 calendar requester filter contract")
class ApiV2CalendarRequesterFilterMVCIT {

  private final WebApplicationContext context;
  private final InstrumentDao instrumentDao;
  private final InstrumentEntityApiManager instrumentApiManager;
  private final PlatformTransactionManager transactionManager;
  private final ApiV2ResourceCatalog catalog;
  private final TimeSlotBookingDao timeSlotBookingDao;
  private final BookingCalendarQuery calendarQuery;

  @Autowired
  ApiV2CalendarRequesterFilterMVCIT(
      WebApplicationContext context,
      InstrumentDao instrumentDao,
      InstrumentEntityApiManager instrumentApiManager,
      PlatformTransactionManager transactionManager,
      ApiV2ResourceCatalog catalog,
      TimeSlotBookingDao timeSlotBookingDao,
      BookingCalendarQuery calendarQuery) {
    this.context = context;
    this.instrumentDao = instrumentDao;
    this.instrumentApiManager = instrumentApiManager;
    this.transactionManager = transactionManager;
    this.catalog = catalog;
    this.timeSlotBookingDao = timeSlotBookingDao;
    this.calendarQuery = calendarQuery;
  }

  private ApiV2Fixture fixture;
  private MockMvc mockMvc;
  private TransactionTemplate transactions;
  private final List<CreatedInstrument> instruments = new ArrayList<>();
  private final List<Long> eventsOfThisTest = new ArrayList<>();
  private User owner;
  private String ownerKey;

  @BeforeEach
  void setUp() {
    fixture = ApiV2Fixture.in(context);
    mockMvc = fixture.mockMvc();
    fixture.enableBookings();
    transactions = new TransactionTemplate(transactionManager);
    owner = fixture.user();
    ownerKey = fixture.userKey();
  }

  @AfterEach
  void tearDown() {
    try {
      deleteInstruments();
    } finally {
      eventsOfThisTest.clear();
      fixture.cleanUp();
    }
  }

  @Test
  @DisplayName("requester fields and filters name nobody on an event shown only as busy")
  void requesterFieldsAndFiltersSkipBusyEvents() throws Exception {
    // The owner reads the requester's items through a lab group; the stranger shares nothing.
    User requester = fixture.otherUser();
    User stranger = fixture.thirdUser();
    owner = fixture.makeOwnerRoleVisibleTo(owner, requester);
    Instant start = alignedStart();
    Instant end = start.plus(1, ChronoUnit.HOURS);
    long ownItem = instrument(owner, "own " + fixture.marker());
    long sharedItem = instrument(requester, "shared " + fixture.marker());
    long strangerItem = instrument(stranger, "stranger " + fixture.marker());
    makePrivate(strangerItem);
    fixture.bookingConfiguration(ownItem, "UTC", ownerKey);
    fixture.bookingConfiguration(sharedItem, "UTC", fixture.otherUserKey());
    fixture.bookingConfiguration(strangerItem, "UTC", fixture.thirdUserKey());
    long ownBooking = fixture.booking(ownItem, start, end, ownerKey);
    long sharedBooking = fixture.booking(sharedItem, start, end, fixture.otherUserKey());
    long strangerBooking = fixture.booking(strangerItem, start, end, fixture.thirdUserKey());
    transactions.executeWithoutResult(
        ignored -> {
          TimeSlotBooking booking = timeSlotBookingDao.get(strangerBooking);
          booking.setState(BookingState.CANCELLED);
          booking.setCancellationReason("Instrument needs recalibration");
        });
    List<Long> scoped = List.of(ownBooking, sharedBooking, strangerBooking);
    eventsOfThisTest.addAll(scoped);

    // The owner can filter another requester's full event, while only their own user reference is
    // readable through the users collection.
    JsonNode shared = bookingDocument(sharedBooking);
    assertEquals("full", shared.path("privacy").asText(), shared.toString());
    assertTrue(shared.path("requester").isNull(), shared.toString());
    JsonNode own = bookingDocument(ownBooking);
    assertEquals(owner.getId(), own.path("requester").path("value").asLong(), own.toString());
    assertBookings(scoped, "requester.value==me", Set.of(ownBooking));
    assertBookings(scoped, "requester.value==" + owner.getId(), Set.of(ownBooking));
    assertBookings(scoped, "requester.value==" + requester.getId(), Set.of(sharedBooking));
    assertBookings(scoped, "requester.value==" + stranger.getId(), Set.of());
    assertBookings(
        scoped, "requester.value!=" + stranger.getId(), Set.of(ownBooking, sharedBooking));

    // The Calendar event filters, its booked-by facet and its Search follow the same rule.
    assertCalendarEvents(start, end, "requester.value==me", null, Set.of(ownBooking));
    assertCalendarEvents(start, end, "targetOwner==me", null, Set.of(ownBooking));
    assertCalendarEvents(start, end, "requester.value==" + owner.getId(), null, Set.of(ownBooking));
    assertCalendarEvents(start, end, "requester.value==" + stranger.getId(), null, Set.of());
    assertCalendarEvents(
        start, end, "requester.username=contains=" + stranger.getUsername(), null, Set.of());
    assertCalendarEvents(
        start, end, "bookedBy=contains=" + requester.getUsername(), null, Set.of(sharedBooking));
    assertCalendarEvents(start, end, "bookedBy=contains=" + stranger.getUsername(), null, Set.of());
    assertCalendarEvents(start, end, null, stranger.getUsername(), Set.of());
    assertCalendarEvents(start, end, null, requester.getUsername(), Set.of(sharedBooking));

    // A busy event has no requester reference, and requesters' filters never match it.
    Map<String, Object> busy = directBookingDocument(strangerBooking);
    assertEquals("busy", busy.get("privacy"));
    assertNull(busy.get("bookedBy"), busy.toString());
    assertNull(busy.get("cancellationReason"), busy.toString());
    assertNull(
        transactions.execute(
            ignored ->
                ApiV2TimeSlotBookingResource.DESCRIPTION
                    .requireRelationship("requester")
                    .binding()
                    .read(timeSlotBookingDao.get(strangerBooking))));
    assertEquals(
        Set.of(),
        directBookings(
            scoped,
            new FilterExpression.Comparison(
                "requesterId", Operator.EQUAL, List.of(stranger.getId()), false)));
    assertEquals(
        Set.of(ownBooking),
        directBookings(
            scoped,
            new FilterExpression.Comparison(
                "requesterId", Operator.EQUAL, List.of(owner.getId()), false)));
    FilterExpression strangerName =
        new FilterExpression.Comparison(
            "requesterUsername", Operator.CONTAINS, List.of(stranger.getUsername()), false);
    assertEquals(Set.of(strangerBooking), directCalendarEvents(scoped, strangerName, false));
    assertEquals(Set.of(), directCalendarEvents(scoped, strangerName, true));
    assertEquals(
        Set.of(ownBooking),
        directCalendarEvents(
            scoped,
            new FilterExpression.Comparison(
                "requesterId", Operator.EQUAL, List.of(owner.getId()), false),
            true));
  }

  private void assertBookings(List<Long> scopedIds, String filter, Set<Long> expected)
      throws Exception {
    String where = "id=in=(" + join(scopedIds) + ");" + filter;
    MvcResult listed =
        mockMvc
            .perform(
                get("/api/v2/bookings")
                    .header("apiKey", ownerKey)
                    .param("where", where)
                    .param("limit", "100")
                    .param("totals", "true")
                    .param("fields[bookings]", "id"))
            .andReturn();
    assertEquals(200, listed.getResponse().getStatus(), listed.getResponse().getContentAsString());
    JsonNode listBody = OBJECT_MAPPER.readTree(listed.getResponse().getContentAsString());
    Set<Long> actual = new HashSet<>();
    listBody.path("docs").forEach(document -> actual.add(document.path("id").asLong()));
    MvcResult counted =
        mockMvc
            .perform(get("/api/v2/bookings/count").header("apiKey", ownerKey).param("where", where))
            .andReturn();
    assertEquals(
        200, counted.getResponse().getStatus(), counted.getResponse().getContentAsString());
    JsonNode countBody = OBJECT_MAPPER.readTree(counted.getResponse().getContentAsString());
    assertAll(
        "booking filter results for " + where,
        () -> assertEquals(expected, actual, "list result set"),
        () -> assertEquals(expected.size(), listBody.path("totalDocs").asLong(), "list total"),
        () -> assertEquals(expected.size(), countBody.path("totalDocs").asLong(), "count total"));
  }

  private void assertCalendarEvents(
      Instant start, Instant end, String where, String search, Set<Long> expected)
      throws Exception {
    var request =
        get("/api/v2/booking-calendar/events")
            .header("apiKey", ownerKey)
            .param("start", start.minus(1, ChronoUnit.HOURS).toString())
            .param("end", end.plus(1, ChronoUnit.HOURS).toString())
            .param("limit", "100");
    if (where != null) {
      request.param("where", where);
    }
    if (search != null) {
      request.param("q", search);
    }
    MvcResult result = mockMvc.perform(request).andReturn();
    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    JsonNode body = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    Set<Long> actual = new HashSet<>();
    body.path("docs").forEach(document -> actual.add(document.path("id").asLong()));
    // Other tests may have events in the same window; only this test's events are compared.
    actual.retainAll(Set.copyOf(eventsOfThisTest));
    assertEquals(expected, actual, "calendar events for where=" + where + " q=" + search);
  }

  private JsonNode bookingDocument(long id) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v2/bookings")
                    .header("apiKey", ownerKey)
                    .param("where", "id==" + id)
                    .param("fields[bookings]", "id,privacy,requester"))
            .andReturn();
    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    JsonNode docs = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString()).path("docs");
    assertEquals(1, docs.size(), docs.toString());
    return docs.get(0);
  }

  /** Lists events without the root read policy, with or without the requester guard. */
  private Set<Long> directBookings(List<Long> scopedIds, FilterExpression requesterFilter) {
    FilterExpression filter =
        new FilterExpression.And(
            List.of(
                new FilterExpression.Comparison("id", Operator.IN, List.copyOf(scopedIds), false),
                BookingRequesterFilters.visibleRequestersOnly(requesterFilter, owner.getId())));
    ResourceRequest request =
        new ResourceRequest(
            filter,
            List.of(new Sort("id", true)),
            new ResourceRequest.Page(1, 100),
            FieldSelection.all(),
            IncludeTree.empty());
    var targetAccess = RelationshipReadAccess.forActor(catalog.registry(), owner);
    return transactions
        .execute(ignored -> timeSlotBookingDao.getReadableResources(request, targetAccess))
        .resources()
        .stream()
        .map(TimeSlotBooking::getId)
        .collect(Collectors.toSet());
  }

  /** Renders one event loaded without the root read policy and without a prepared full view. */
  private Map<String, Object> directBookingDocument(long id) {
    var targetAccess = RelationshipReadAccess.forActor(catalog.registry(), owner);
    return transactions.execute(
        ignored ->
            ApiV2TimeSlotBookingResource.DESCRIPTION.toDocument(
                timeSlotBookingDao.findReadableById(id, targetAccess).orElseThrow()));
  }

  /**
   * Lists events without the root read policy under a Calendar event filter, either as the Calendar
   * compiles it or as the bare comparison.
   */
  private Set<Long> directCalendarEvents(
      List<Long> scopedIds, FilterExpression requesterFilter, boolean guarded) {
    var registration = catalog.find("bookings").orElseThrow();
    var runtime = registration.runtimeFieldContext(owner);
    var calendar =
        ApiV2TimeSlotBookingResource.calendarFilterDescription(
            registration.description().accessPolicy().readAccess());
    ResourceRequest filter =
        new ResourceRequest(
            requesterFilter,
            List.of(new Sort("id", true)),
            new ResourceRequest.Page(1, 100),
            FieldSelection.all(),
            IncludeTree.empty());
    ResourceRequest scope =
        ApiV2ResourceRequestParser.filtered(
            "id=in=(" + join(scopedIds) + ")",
            registration.description(),
            catalog.registry(),
            runtime);
    ResourceRequest request =
        new ResourceRequest(
            scope.filter(),
            null,
            scope.sort(),
            new ResourceRequest.Page(1, 100),
            scope.fieldSelections(),
            scope.includes(),
            scope.runtime());
    var targetAccess = RelationshipReadAccess.forActor(catalog.registry(), owner);
    return transactions
        .execute(
            ignored ->
                timeSlotBookingDao.getCalendarResources(
                    request,
                    targetAccess,
                    guarded
                        ? calendarQuery.eventFilter(filter, null, owner)
                        : new RsqlCollectionQuery(calendar, "booking", "bareCalendarFilter")
                            .translate(filter.filter(), targetAccess, filter.runtime())))
        .resources()
        .stream()
        .map(TimeSlotBooking::getId)
        .collect(Collectors.toSet());
  }

  private static Instant alignedStart() {
    Instant candidate = Instant.now().plus(7, ChronoUnit.DAYS);
    return Instant.ofEpochSecond(((candidate.getEpochSecond() + 899) / 900) * 900);
  }

  private long instrument(User instrumentOwner, String name) {
    return ApiV2FilterExpansionMVCIT.instrument(fixture, instruments, instrumentOwner, name);
  }

  private void makePrivate(long instrumentId) {
    ApiV2FilterExpansionMVCIT.makePrivate(transactions, instrumentDao, instrumentId);
  }

  private static String join(List<Long> ids) {
    return ApiV2FilterExpansionMVCIT.join(ids);
  }

  private void deleteInstruments() {
    ApiV2FilterExpansionMVCIT.deleteInstruments(instrumentApiManager, instruments);
  }
}
