package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import java.util.List;
import java.util.Optional;

/** Shared domain service for reading and changing booking configurations. */
public interface BookingConfigurationManager {

  /** Values accepted when creating a booking configuration. */
  record Create(boolean enabled, String timeZone, ResolvedBookableTarget target) {}

  /** Values accepted when changing a booking configuration; {@code null} means unchanged. */
  record Patch(Boolean enabled, String timeZone, ResolvedBookableTarget target) {}

  /** Returns one page selected by a parsed collection request. */
  ResourcePage<BookingConfiguration> getConfigurations(ResourceRequest request, User actor);

  /** Counts configurations selected by a parsed collection request. */
  long countConfigurations(ResourceRequest request, User actor);

  /** Finds one configuration without throwing when it is absent. */
  Optional<BookingConfiguration> getConfiguration(Long id, User actor);

  /** Authorizes, validates, and persists a new booking configuration. */
  BookingConfiguration createConfiguration(Create create, User actor);

  /** Authorizes and atomically validates and persists configurations in input order. */
  List<BookingConfiguration> createConfigurations(List<Create> creates, User actor);

  /** Authorizes and applies a validated change when the configuration exists. */
  Optional<BookingConfiguration> updateConfiguration(Long id, Patch patch, User actor);

  /** Authorizes and atomically applies a validated change to the selected configurations. */
  List<BookingConfiguration> updateConfigurations(ResourceRequest request, Patch patch, User actor);

  /** Authorizes and removes one configuration, returning its previous value when present. */
  Optional<BookingConfiguration> removeConfiguration(Long id, User actor);

  /** Authorizes and atomically removes the selected configurations. */
  List<BookingConfiguration> removeConfigurations(ResourceRequest request, User actor);
}
