package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import java.time.Instant;

/** Server-owned Calendar event scope, separate from conflict and availability queries. */
public interface BookingCalendarSearchManager {
  /** Returns visible events after applying filters, overlap, state and text before pagination. */
  ResourcePage<TimeSlotBooking> events(
      ResourceRequest request, Instant start, Instant end, String text, User caller);
}
