package com.researchspace.booking.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.testutils.SpringTransactionalTest;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TimeSlotBookingDaoTest extends SpringTransactionalTest {

  private final TimeSlotBookingDao bookingDao;

  @Autowired
  TimeSlotBookingDaoTest(TimeSlotBookingDao bookingDao) {
    this.bookingDao = bookingDao;
  }

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
        bookingDao
            .findFirstOverlap(
                configuration.getId(),
                instant("2026-08-17T09:00:00Z"),
                instant("2026-08-17T10:00:00Z"),
                null)
            .isPresent());
    assertFalse(
        bookingDao
            .findFirstOverlap(
                configuration.getId(),
                instant("2026-08-17T11:00:00Z"),
                instant("2026-08-17T12:00:00Z"),
                null)
            .isPresent());
    assertEquals(
        booking.getId(),
        bookingDao
            .findFirstOverlap(
                configuration.getId(),
                instant("2026-08-17T10:59:00Z"),
                instant("2026-08-17T11:01:00Z"),
                null)
            .orElseThrow()
            .getId());
    assertFalse(
        bookingDao
            .findFirstOverlap(
                configuration.getId(),
                instant("2026-08-17T10:30:00Z"),
                instant("2026-08-17T10:45:00Z"),
                booking.getId())
            .isPresent());
  }

  @Test
  void returnsTheEarliestOverlappingRow() {
    User requester = createInitAndLoginAnyUser();
    BookingConfiguration configuration = configurationFor(requester, "Earliest scope");
    bookingFor(
        configuration,
        requester,
        "2026-08-17T12:00:00Z",
        "2026-08-17T13:00:00Z",
        BookingState.CONFIRMED,
        false);
    TimeSlotBooking earliest =
        bookingFor(
            configuration,
            requester,
            "2026-08-17T10:00:00Z",
            "2026-08-17T11:00:00Z",
            BookingState.CONFIRMED,
            false);

    assertEquals(
        earliest.getId(),
        bookingDao
            .findFirstOverlap(
                configuration.getId(),
                instant("2026-08-17T09:00:00Z"),
                instant("2026-08-17T14:00:00Z"),
                null)
            .orElseThrow()
            .getId());
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
        bookingDao
            .findFirstOverlap(
                configuration.getId(),
                instant("2026-08-17T10:30:00Z"),
                instant("2026-08-17T12:30:00Z"),
                null)
            .isPresent());
  }

  private BookingConfiguration configurationFor(User owner, String name) {
    var session = sessionFactory.getCurrentSession();
    BookingConfiguration configuration =
        BookingDaoTestFixtures.configuration(
            session, owner, createBasicInstrumentForUser(owner, name).getId());
    session.flush();
    return configuration;
  }

  private TimeSlotBooking bookingFor(
      BookingConfiguration configuration,
      User requester,
      String start,
      String end,
      BookingState state,
      boolean deleted) {
    return BookingDaoTestFixtures.booking(
        sessionFactory.getCurrentSession(),
        configuration,
        requester,
        instant(start),
        instant(end),
        state,
        deleted,
        new Date(),
        new Date());
  }

  private static Date instant(String value) {
    return Date.from(Instant.parse(value));
  }
}
