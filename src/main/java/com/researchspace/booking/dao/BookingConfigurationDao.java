package com.researchspace.booking.dao;

import com.researchspace.dao.CollectionDao;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.collection.ResourceRequest;
import java.util.List;
import java.util.Optional;

/** Persistence operations specific to booking configurations. */
public interface BookingConfigurationDao extends CollectionDao<BookingConfiguration, Long> {

  /** Finds the configuration assigned to the complete target identity. */
  Optional<BookingConfiguration> findByTarget(BookableTargetReference target);

  /** Returns all rows matching a request so caller-specific target permissions can be applied. */
  List<BookingConfiguration> getAllResources(ResourceRequest request);

  /** Saves and flushes one configuration so unique-target races fail inside the manager call. */
  BookingConfiguration saveAndFlush(BookingConfiguration configuration);
}
