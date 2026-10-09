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

  /** Applies Calendar text scope before event paging and counts. */
  ResourcePage<TimeSlotBooking> getCalendarResources(
      ResourceRequest request,
      RelationshipReadAccess access,
      com.researchspace.dao.query.RsqlCollectionQuery.Predicate restriction);

  /** Returns a page after target read rules and soft deletion are applied in SQL. */
  ResourcePage<TimeSlotBooking> getReadableResources(
      ResourceRequest request, RelationshipReadAccess targetAccess);

  /** Counts rows after target read rules and soft deletion are applied in SQL. */
  long countReadableResources(ResourceRequest request, RelationshipReadAccess targetAccess);

  /** Returns one readable, non-deleted booking. */
  Optional<TimeSlotBooking> findReadableById(Long id, RelationshipReadAccess targetAccess);

  /**
   * Returns the earliest current confirmed, non-deleted row that overlaps a half-open interval,
   * using a locking read, not an earlier transaction snapshot. Callers must hold the configuration
   * lock first.
   */
  Optional<TimeSlotBooking> findFirstOverlap(
      Long configurationId,
      Date start,
      Date end,
      Long excludedBookingId,
      Set<BookingEventKind> includedKinds);

  /** Finds the earliest overlapping row of either persisted event kind. */
  default Optional<TimeSlotBooking> findFirstOverlap(
      Long configurationId, Date start, Date end, Long excludedBookingId) {
    return findFirstOverlap(
        configurationId,
        start,
        end,
        excludedBookingId,
        Set.of(BookingEventKind.BOOKING, BookingEventKind.MAINTENANCE));
  }

  /** The interval and kind of one confirmed event, for availability evaluation. */
  record EventInterval(long configurationId, BookingEventKind kind, Date start, Date end) {}

  /**
   * Returns the confirmed, non-deleted events of these configurations that overlap the half-open
   * interval {@code [start, end)}. It applies no read rule: callers pass only configurations the
   * caller may read, whose events that caller may read too ({@code BookingEventReadAccess}).
   */
  List<EventInterval> findConfirmedEventIntervals(
      Collection<Long> configurationIds, Date start, Date end);

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
