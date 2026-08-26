package com.researchspace.booking.dao;

import com.researchspace.dao.CollectionDao;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookingConfiguration;
import java.util.Optional;

/** Persistence operations specific to booking configurations. */
public interface BookingConfigurationDao extends CollectionDao<BookingConfiguration, Long> {

  /** Finds the configuration assigned to the complete target identity. */
  Optional<BookingConfiguration> findByTarget(BookableTargetReference target);

  /** Locks and returns the configuration for one complete target identity. */
  Optional<BookingConfiguration> lockByTarget(BookableTargetReference target);

  /** Locks and returns one configuration by its identifier. */
  Optional<BookingConfiguration> lockById(Long id);

  /** Saves and flushes one configuration so unique-target races fail inside the manager call. */
  BookingConfiguration saveAndFlush(BookingConfiguration configuration);
}
