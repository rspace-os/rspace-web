package com.researchspace.booking.dao;

import com.researchspace.dao.CollectionDao;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import java.util.Collection;
import java.util.Date;
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

  /** Tests a half-open interval against confirmed, non-deleted rows. */
  boolean overlaps(Long configurationId, Date start, Date end, Long excludedBookingId);

  /** Returns target IDs owned by the actor in one query. */
  Set<Long> findOwnedInstrumentIds(Collection<Long> targetIds, Long actorId);

  /** Saves and flushes one booking inside its configuration lock transaction. */
  TimeSlotBooking saveAndFlush(TimeSlotBooking booking);
}
