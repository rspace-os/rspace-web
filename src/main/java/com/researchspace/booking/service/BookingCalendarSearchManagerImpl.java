package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.booking.dao.BookingCalendarQuery;
import com.researchspace.booking.dao.BookingItemQuery;
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
  /** The event's target reference in the bookings collection query. */
  private static final String EVENT_TARGET = "booking.bookingConfiguration.target";

  private final TimeSlotBookingManager bookings;
  private final BookingCalendarQuery query;
  private final BookingItemQuery itemQuery;
  private final FeatureFlagManager flags;
  private final BookingLocationFilterManager locations;

  public BookingCalendarSearchManagerImpl(
      TimeSlotBookingManager bookings,
      BookingCalendarQuery query,
      BookingItemQuery itemQuery,
      FeatureFlagManager flags,
      BookingLocationFilterManager locations) {
    this.bookings = bookings;
    this.query = query;
    this.itemQuery = itemQuery;
    this.flags = flags;
    this.locations = locations;
  }

  /** Returns one visible event page under the complete item, event, interval and search scope. */
  @Override
  public ResourcePage<TimeSlotBooking> events(
      ResourceRequest request,
      Instant start,
      Instant end,
      String text,
      boolean ownedByCaller,
      User caller) {
    if (!flags.isFeatureFlagEnabled(BOOKING_ENABLED, caller)) throw new NotFoundException();
    // Item filters reused as event filters may name a location, which only Inventory can resolve.
    BookingLocationFilterManager.Resolved scoped =
        locations.resolveLocations(request, caller, EVENT_TARGET);
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
        BookingItemQuery.and(
            BookingItemQuery.and(
                query.eventFilter(scoped.request(), text, caller), scoped.restriction()),
            ownedByCaller ? itemQuery.ownedBy(caller, EVENT_TARGET) : null));
  }
}
