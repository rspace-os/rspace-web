package com.researchspace.booking.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.testutils.SpringTransactionalTest;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class TimeSlotBookingDaoTest extends SpringTransactionalTest {

  @Autowired private TimeSlotBookingDao bookingDao;

  @Test
  void usesHalfOpenOverlapBoundariesAndCanExcludeTheEditedBooking() {
    User requester = createInitAndLoginAnyUser();
    BookingConfiguration configuration = configurationFor(requester, "Boundary scope");
    TimeSlotBooking booking =
        bookingFor(
            configuration,
            requester,
            "2026-08-17T10:00:00Z",
            "2026-08-17T11:00:00Z",
            BookingState.CONFIRMED,
            false);

    assertFalse(
        bookingDao.overlaps(
            configuration.getId(),
            instant("2026-08-17T09:00:00Z"),
            instant("2026-08-17T10:00:00Z"),
            null));
    assertFalse(
        bookingDao.overlaps(
            configuration.getId(),
            instant("2026-08-17T11:00:00Z"),
            instant("2026-08-17T12:00:00Z"),
            null));
    assertTrue(
        bookingDao.overlaps(
            configuration.getId(),
            instant("2026-08-17T10:59:00Z"),
            instant("2026-08-17T11:01:00Z"),
            null));
    assertFalse(
        bookingDao.overlaps(
            configuration.getId(),
            instant("2026-08-17T10:30:00Z"),
            instant("2026-08-17T10:45:00Z"),
            booking.getId()));
  }

  @Test
  void ignoresCancelledAndDeletedRows() {
    User requester = createInitAndLoginAnyUser();
    BookingConfiguration configuration = configurationFor(requester, "Cancelled scope");
    bookingFor(
        configuration,
        requester,
        "2026-08-17T10:00:00Z",
        "2026-08-17T11:00:00Z",
        BookingState.CANCELLED,
        false);
    bookingFor(
        configuration,
        requester,
        "2026-08-17T12:00:00Z",
        "2026-08-17T13:00:00Z",
        BookingState.CONFIRMED,
        true);

    assertFalse(
        bookingDao.overlaps(
            configuration.getId(),
            instant("2026-08-17T10:30:00Z"),
            instant("2026-08-17T12:30:00Z"),
            null));
  }

  private BookingConfiguration configurationFor(User owner, String name) {
    Long instrumentId = createBasicInstrumentForUser(owner, name).getId();
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setEnabled(true);
    configuration.setTimeZone("UTC");
    configuration.replaceTarget(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, instrumentId));
    sessionFactory.getCurrentSession().persist(configuration);
    sessionFactory.getCurrentSession().flush();
    return configuration;
  }

  private TimeSlotBooking bookingFor(
      BookingConfiguration configuration,
      User requester,
      String start,
      String end,
      BookingState state,
      boolean deleted) {
    TimeSlotBooking booking = new TimeSlotBooking();
    booking.setBookingConfiguration(configuration);
    booking.setRequester(requester);
    booking.setStartTime(instant(start));
    booking.setEndTime(instant(end));
    booking.setState(state);
    booking.setDeleted(deleted);
    booking.setCreatedAt(new Date());
    booking.setUpdatedAt(new Date());
    booking.setCreatedBy(requester);
    booking.setUpdatedBy(requester);
    sessionFactory.getCurrentSession().persist(booking);
    sessionFactory.getCurrentSession().flush();
    return booking;
  }

  private static Date instant(String value) {
    return Date.from(Instant.parse(value));
  }
}
