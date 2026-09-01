package com.researchspace.booking.dao;

import com.researchspace.dao.CollectionDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourceRequest;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Persistence operations specific to booking configurations. */
public interface BookingConfigurationDao extends CollectionDao<BookingConfiguration, Long> {

  /** Finds the configuration assigned to the complete target identity. */
  Optional<BookingConfiguration> findByTarget(BookableTargetReference target);

  /** Locks and returns the configuration for one complete target identity. */
  Optional<BookingConfiguration> lockByTarget(BookableTargetReference target);

  /** Locks and returns one configuration by its identifier. */
  Optional<BookingConfiguration> lockById(Long id);

  /** Finds one archived configuration without making it visible to ordinary reads. */
  Optional<BookingConfiguration> findArchivedById(Long id);

  /** Locks one archived configuration for a lifecycle transition. */
  Optional<BookingConfiguration> lockArchivedById(Long id);

  /** Locks at most {@code limit} current rows selected by the server-side bulk command. */
  List<BookingConfiguration> lockResources(
      ResourceRequest request, int limit, RelationshipReadAccess relationshipAccess);

  /** Locks the active or archived configuration assigned to the complete target identity. */
  Optional<BookingConfiguration> lockByTargetIncludingArchived(BookableTargetReference target);

  /** Saves and flushes one configuration so unique-target races fail inside the manager call. */
  BookingConfiguration saveAndFlush(BookingConfiguration configuration);

  /**
   * Finds active, enabled Instrument targets readable through the caller's current Booking role.
   */
  Set<Long> findBookableInstrumentIds(User caller, Set<String> readableRoleKeys);
}
