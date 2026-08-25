package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.booking.BookingPrivacy;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/** Domain service for one-off time-slot bookings. */
public interface TimeSlotBookingManager {

  /** Values accepted when a caller creates one booking. */
  record Create(ResolvedBookableTarget target, Date start, Date end, String purpose) {}

  /** Values accepted when a caller changes one booking. */
  record Patch(Date start, Date end, boolean purposeSupplied, String purpose, BookingState state) {}

  /** One immutable, privacy-shaped booking event for calendar serialization. */
  record CalendarEvent(
      Long id,
      Date start,
      Date end,
      Date createdAt,
      Date updatedAt,
      BookingPrivacy privacy,
      String bookedBy,
      String purpose,
      boolean canEdit) {

    public CalendarEvent {
      start = copy(start);
      end = copy(end);
      createdAt = copy(createdAt);
      updatedAt = copy(updatedAt);
    }

    @Override
    public Date start() {
      return copy(start);
    }

    @Override
    public Date end() {
      return copy(end);
    }

    @Override
    public Date createdAt() {
      return copy(createdAt);
    }

    @Override
    public Date updatedAt() {
      return copy(updatedAt);
    }

    private static Date copy(Date value) {
      return value == null ? null : new Date(value.getTime());
    }
  }

  /** Complete input for one item-scoped calendar. */
  record CalendarSource(String itemName, String timeZone, List<CalendarEvent> events) {

    public CalendarSource {
      events = List.copyOf(events);
    }
  }

  /** Signals that a complete calendar cannot fit within the configured event limit. */
  final class CalendarSourceTooLargeException extends RuntimeException {

    private static final long serialVersionUID = 1L;
  }

  /** Returns one page selected by a parsed collection request. */
  ResourcePage<TimeSlotBooking> getBookings(ResourceRequest request, User actor);

  /** Counts bookings selected by a parsed collection request. */
  long countBookings(ResourceRequest request, User actor);

  /** Finds one readable booking and prepares its safe response view. */
  Optional<TimeSlotBooking> getBooking(Long id, User actor);

  /** Returns one readable item's privacy-shaped calendar source or empty when unavailable. */
  Optional<CalendarSource> getCalendarSource(
      Long configurationId, User actor, Date refreshedAt, int maxEvents);

  /** Creates one booking as subject and retains the originating actor for audit. */
  TimeSlotBooking createBooking(Create create, User subject, User actor);

  /** Updates one booking as subject and retains the originating actor for audit. */
  Optional<TimeSlotBooking> updateBooking(Long id, Patch patch, User subject, User actor);
}
