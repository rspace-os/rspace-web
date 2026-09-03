package com.researchspace.booking.dao;

import com.researchspace.dao.CollectionDao;
import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Persistence operations for one-off time-slot bookings. */
public interface TimeSlotBookingDao extends CollectionDao<TimeSlotBooking, Long> {

  /** Returns a page after target read rules and soft deletion are applied in SQL. */
  ResourcePage<TimeSlotBooking> getReadableResources(
      ResourceRequest request, RelationshipReadAccess targetAccess);

  /** Counts rows after target read rules and soft deletion are applied in SQL. */
  long countReadableResources(ResourceRequest request, RelationshipReadAccess targetAccess);

  /** Returns one readable, non-deleted booking. */
  Optional<TimeSlotBooking> findReadableById(Long id, RelationshipReadAccess targetAccess);

  /** Returns one readable booking for audit, including a soft-deleted cancellation. */
  Optional<TimeSlotBooking> findReadableForAuditById(
      ResourceRequest authorizedRequest, RelationshipReadAccess targetAccess);

  /** Tests a half-open interval against confirmed, non-deleted rows. */
  boolean overlaps(
      Long configurationId,
      Date start,
      Date end,
      Long excludedBookingId,
      Set<BookingEventKind> includedKinds);

  /** Tests an interval against both persisted event kinds. */
  default boolean overlaps(Long configurationId, Date start, Date end, Long excludedBookingId) {
    return overlaps(
        configurationId,
        start,
        end,
        excludedBookingId,
        Set.of(BookingEventKind.BOOKING, BookingEventKind.MAINTENANCE));
  }

  /** Returns target IDs owned by the actor in one query. */
  Set<Long> findOwnedInstrumentIds(Collection<Long> targetIds, Long actorId);

  /** Returns the ordered, fetch-complete rows used to build one calendar feed. */
  List<TimeSlotBooking> findCalendarBookings(Long configurationId, Date cutoff, int maximumRows);

  /** Returns the ordered, fetch-complete rows used to build one user's calendar feed. */
  List<TimeSlotBooking> findUserCalendarBookings(Long userId, Date cutoff, int maximumRows);

  /** Future confirmed rows exposed by an authorised archived summary. */
  List<TimeSlotBooking> findFutureConfirmedByConfiguration(Long configurationId, Date now);

  /** Saves and flushes one booking inside its configuration lock transaction. */
  TimeSlotBooking saveAndFlush(TimeSlotBooking booking);

  /** Removes every live booking for a configuration in bounded audited entity batches. */
  int removeAllByConfigurationId(Long configurationId);
}
