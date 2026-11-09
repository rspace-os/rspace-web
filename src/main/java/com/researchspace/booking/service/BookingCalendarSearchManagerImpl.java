package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.booking.dao.BookingCalendarQuery;
import com.researchspace.model.User;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.service.FeatureFlagManager;
import jakarta.ws.rs.NotFoundException;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** Applies Calendar scope before event pagination without changing availability queries. */
@Service
public class BookingCalendarSearchManagerImpl implements BookingCalendarSearchManager {
  private final TimeSlotBookingManager bookings;
  private final BookingCalendarQuery query;
  private final FeatureFlagManager flags;

  public BookingCalendarSearchManagerImpl(
      TimeSlotBookingManager bookings, BookingCalendarQuery query, FeatureFlagManager flags) {
    this.bookings = bookings;
    this.query = query;
    this.flags = flags;
  }

  /** Returns one visible event page under the complete item, event, interval and search scope. */
  @Override
  public ResourcePage<TimeSlotBooking> events(
      ResourceRequest request, Instant start, Instant end, String text, User caller) {
    if (!flags.isFeatureFlagEnabled(BOOKING_ENABLED, caller)) throw new NotFoundException();
    ResourceRequest unfiltered =
        new ResourceRequest(
            null,
            request.serverConstraint(),
            request.sort(),
            request.page(),
            request.fieldSelections(),
            request.includes(),
            request.runtime());
    return bookings.getBookings(
        unfiltered.restrict(BookingCalendarQuery.interval(start, end)),
        caller,
        query.eventFilter(request, text, caller));
  }
}
