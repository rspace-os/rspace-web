package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import java.util.Date;
import java.util.Optional;

/** Domain service for one-off time-slot bookings. */
public interface TimeSlotBookingManager {

  /** Values accepted when a caller creates one booking. */
  record Create(ResolvedBookableTarget target, Date start, Date end, String purpose) {}

  /** Values accepted when a caller changes one booking. */
  record Patch(Date start, Date end, boolean purposeSupplied, String purpose, BookingState state) {}

  /** Returns one page selected by a parsed collection request. */
  ResourcePage<TimeSlotBooking> getBookings(ResourceRequest request, User actor);

  /** Counts bookings selected by a parsed collection request. */
  long countBookings(ResourceRequest request, User actor);

  /** Finds one readable booking and prepares its safe response view. */
  Optional<TimeSlotBooking> getBooking(Long id, User actor);

  /** Creates one booking as subject and retains the originating actor for audit. */
  TimeSlotBooking createBooking(Create create, User subject, User actor);

  /** Updates one booking as subject and retains the originating actor for audit. */
  Optional<TimeSlotBooking> updateBooking(Long id, Patch patch, User subject, User actor);
}
