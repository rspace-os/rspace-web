package com.researchspace.booking.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.testutils.SpringTransactionalTest;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class BookingRelationshipReadRestrictionTest extends SpringTransactionalTest {

  @Autowired private TimeSlotBookingDao bookingDao;

  @Test
  void removesUnreadableTargetsBeforePaginationAndCount() {
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

  private static ResourceRequest oneRowPage() {
    return new ResourceRequest(
        null,
        List.of(new Sort("id", true)),
        new ResourceRequest.Page(1, 1),
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
                ApiV2TimeSlotBookingResource.DESCRIPTION,
                ApiV2UserResource.DESCRIPTION,
                ApiV2InstrumentResource.description(access)));
    return RelationshipReadAccess.forActor(registry, null);
  }

  private TimeSlotBooking bookingFor(User requester, String instrumentName, boolean deleted) {
    Long instrumentId = createBasicInstrumentForUser(requester, instrumentName).getId();
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setEnabled(true);
    configuration.setTimeZone("UTC");
    configuration.replaceTarget(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, instrumentId));
    sessionFactory.getCurrentSession().persist(configuration);

    TimeSlotBooking booking = new TimeSlotBooking();
    booking.setBookingConfiguration(configuration);
    booking.setRequester(requester);
    booking.setStartTime(Date.from(Instant.parse("2026-08-17T10:00:00Z")));
    booking.setEndTime(Date.from(Instant.parse("2026-08-17T11:00:00Z")));
    booking.setState(BookingState.CONFIRMED);
    booking.setDeleted(deleted);
    booking.setCreatedBy(requester);
    booking.setUpdatedBy(requester);
    sessionFactory.getCurrentSession().persist(booking);
    sessionFactory.getCurrentSession().flush();
    return booking;
  }
}
