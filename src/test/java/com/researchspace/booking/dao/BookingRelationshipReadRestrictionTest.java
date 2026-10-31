package com.researchspace.booking.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourceMember;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.testutils.SpringTransactionalTest;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BookingRelationshipReadRestrictionTest extends SpringTransactionalTest {

  private final TimeSlotBookingDao bookingDao;

  @Autowired
  BookingRelationshipReadRestrictionTest(TimeSlotBookingDao bookingDao) {
    this.bookingDao = bookingDao;
  }

  @Test
  void pageFetchesActorsAndAccessAlongsideConfigurations() {
    User requester = createInitAndLoginAnyUser();
    TimeSlotBooking created = bookingFor(requester, "Fetched scope", false);
    var session = sessionFactory.getCurrentSession();
    session.flush();
    session.clear();

    TimeSlotBooking loaded =
        bookingDao
            .getReadableResources(oneRowPage(), targetsNamed("Fetched scope"))
            .resources()
            .get(0);
    assertEquals(created.getId(), loaded.getId());
    assertTrue(org.hibernate.Hibernate.isInitialized(loaded.getRequester()), "requester");
    assertTrue(org.hibernate.Hibernate.isInitialized(loaded.getCreatedBy()), "creator");
    assertTrue(
        org.hibernate.Hibernate.isInitialized(loaded.getBookingConfiguration().getResourceAccess()),
        "configuration access");
  }

  @Test
  void filteringThroughTargetsAppliesTheirReadRuleBeforePaginationAndCount() {
    User requester = createInitAndLoginAnyUser();
    TimeSlotBooking readable = bookingFor(requester, "Readable scope", false);
    bookingFor(requester, "Hidden scope", false);
    bookingFor(requester, "Readable deleted scope", true);

    ResourcePage<TimeSlotBooking> page =
        bookingDao.getReadableResources(oneRowPage(), targetsNamed("Readable scope"));

    assertEquals(
        List.of(readable.getId()), page.resources().stream().map(TimeSlotBooking::getId).toList());
    assertEquals(1, page.total());
    assertEquals(
        1, bookingDao.countReadableResources(oneRowPage(), targetsNamed("Readable scope")));
    assertEquals(0, bookingDao.countReadableResources(oneRowPage(), targetsNamed("Nothing")));
  }

  @Test
  void combinesRequesterAndTargetFiltersWithTargetReadRulesForPageAndCount() {
    User matchingRequester = createInitAndLoginAnyUser();
    User otherRequester = createAndSaveUserIfNotExists(getRandomAlphabeticString("otherRequester"));
    TimeSlotBooking unreadable = bookingFor(matchingRequester, "Hidden requester scope", false);
    TimeSlotBooking readable = bookingFor(matchingRequester, "Readable requester scope", false);
    bookingFor(otherRequester, "Readable requester scope", false);

    assertTrue(unreadable.getId() < readable.getId());

    ResourceRequest request =
        new ResourceRequest(
                new FilterExpression.Comparison(
                    "requesterId", Operator.EQUAL, List.of(matchingRequester.getId()), false),
                List.of(new Sort("id", true)),
                new ResourceRequest.Page(1, 1),
                FieldSelection.all(),
                IncludeTree.empty())
            .restrict(
                new FilterExpression.Comparison(
                    "target.name", Operator.CONTAINS, List.of("scope"), false));
    RelationshipReadAccess access = targetsNamed("Readable requester scope");

    ResourcePage<TimeSlotBooking> page = bookingDao.getReadableResources(request, access);

    assertEquals(
        List.of(readable.getId()), page.resources().stream().map(TimeSlotBooking::getId).toList());
    assertEquals(1, page.total());
    assertEquals(page.total(), bookingDao.countReadableResources(request, access));
  }

  @Test
  void appliesUpcomingAndPastEndBoundaries() {
    User requester = createInitAndLoginAnyUser();
    Instant boundary = Instant.parse("2026-08-17T11:00:00Z");
    Date asOf = Date.from(boundary);
    TimeSlotBooking inProgress =
        bookingFor(
            requester,
            "Time boundary scope",
            false,
            Date.from(boundary.minusSeconds(3600)),
            Date.from(boundary.plusSeconds(3600)));
    TimeSlotBooking endsAtBoundary =
        bookingFor(
            requester, "Time boundary scope", false, Date.from(boundary.minusSeconds(3600)), asOf);
    RelationshipReadAccess access = targetsNamed("Time boundary scope");

    ResourcePage<TimeSlotBooking> upcoming =
        bookingDao.getReadableResources(
            requesterEndRequest(requester, Operator.GREATER_THAN, asOf), access);
    ResourcePage<TimeSlotBooking> past =
        bookingDao.getReadableResources(
            requesterEndRequest(requester, Operator.LESS_THAN_OR_EQUAL, asOf), access);

    assertEquals(
        List.of(inProgress.getId()),
        upcoming.resources().stream().map(TimeSlotBooking::getId).toList());
    assertEquals(
        List.of(endsAtBoundary.getId()),
        past.resources().stream().map(TimeSlotBooking::getId).toList());
  }

  private static ResourceRequest oneRowPage() {
    return new ResourceRequest(
        new FilterExpression.Comparison("target.name", Operator.CONTAINS, List.of("scope"), false),
        List.of(new Sort("id", true)),
        new ResourceRequest.Page(1, 1),
        FieldSelection.all(),
        IncludeTree.empty());
  }

  private static ResourceRequest requesterEndRequest(User requester, Operator operator, Date asOf) {
    return new ResourceRequest(
        new FilterExpression.And(
            List.of(
                new FilterExpression.Comparison(
                    "requesterId", Operator.EQUAL, List.of(requester.getId()), false),
                new FilterExpression.Comparison("end", operator, List.of(asOf), false))),
        List.of(new Sort("id", true)),
        new ResourceRequest.Page(1, 10),
        FieldSelection.all(),
        IncludeTree.empty());
  }

  private static RelationshipReadAccess targetsNamed(String name) {
    FilterExpression constraint =
        new FilterExpression.Comparison("name", Operator.EQUAL, List.of(name), false);
    AccessFunction access =
        AccessFunction.documented(
            "Test instrument read access.",
            Set.of(),
            ignored -> AccessResult.allowedWhere(constraint));
    ResourceRegistry registry =
        new ResourceRegistry(
            List.of(
                com.researchspace.inventory.model.ApiV2ContainerResource.DESCRIPTION,
                ApiV2TimeSlotBookingResource.DESCRIPTION,
                ApiV2UserResource.DESCRIPTION,
                new CollectionDescription<>(
                    ApiV2InstrumentResource.RESOURCE_NAME,
                    ApiV2InstrumentResource.DESCRIPTION.entityType(),
                    Stream.<ResourceMember<Instrument>>concat(
                            ApiV2InstrumentResource.DESCRIPTION.fields().stream(),
                            ApiV2InstrumentResource.DESCRIPTION.relationships().stream())
                        .toList(),
                    ApiV2InstrumentResource.DESCRIPTION.idField(),
                    ApiV2InstrumentResource.DESCRIPTION.defaultSort(),
                    AccessPolicy.readOnly(access))));
    return RelationshipReadAccess.forActor(registry, null);
  }

  private TimeSlotBooking bookingFor(User requester, String instrumentName, boolean deleted) {
    return bookingFor(
        requester,
        instrumentName,
        deleted,
        Date.from(Instant.parse("2026-08-17T10:00:00Z")),
        Date.from(Instant.parse("2026-08-17T11:00:00Z")));
  }

  private TimeSlotBooking bookingFor(
      User requester, String instrumentName, boolean deleted, Date start, Date end) {
    Long instrumentId = createBasicInstrumentForUser(requester, instrumentName).getId();
    var session = sessionFactory.getCurrentSession();
    BookingConfiguration configuration =
        BookingDaoTestFixtures.configuration(session, requester, instrumentId);
    return BookingDaoTestFixtures.booking(
        session, configuration, requester, start, end, BookingState.CONFIRMED, deleted, null, null);
  }
}
