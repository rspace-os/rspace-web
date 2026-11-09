package com.researchspace.dao;

import com.researchspace.booking.service.BookingCatalogueLocation;
import com.researchspace.model.User;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentReadSummary;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Booking-specific instrument queries; callers must retain an active transaction. */
public interface BookingInstrumentQueryDao {

  /** Scalar Inventory permission facts used when projecting Booking configuration access. */
  record BookingItemAccess(String ownerUsername, boolean directEdit) {}

  /**
   * Returns one database-paginated page of immediate parent Containers that are both readable in
   * Inventory and attached to an active, enabled Booking configuration readable by the caller.
   */
  ResourcePage<BookingCatalogueLocation> getBookingCatalogueLocations(
      String query, int page, int limit, User caller);

  /** Returns readable immediate parent locations for catalogue rows. */
  Map<Long, BookingCatalogueLocation> getReadableParentLocationSummaries(
      Set<Long> instrumentIds, User caller);

  /**
   * As {@link #getBookingCatalogueLocations(String, int, int, User)}, restricted to the given
   * parent Container IDs when {@code containerIds} is not null. Containers and workbenches share
   * one ID space, so one set matches either kind.
   */
  ResourcePage<BookingCatalogueLocation> getBookingCatalogueLocations(
      String query, Set<Long> containerIds, int page, int limit, User caller);

  /**
   * Returns active Instruments that have a Booking configuration and are stored directly in a
   * non-deleted parent Container the caller may read. A null {@code parentIds} matches any readable
   * parent; an empty set matches nothing. Containers and workbenches share one ID space.
   */
  Set<Long> findConfiguredInstrumentIdsInReadableParents(Set<Long> parentIds, User caller);

  /** Returns only the safe scalar fields Booking may reveal, independent of Inventory sharing. */
  Map<Long, InstrumentReadSummary> getBookingSummaries(Set<Long> instrumentIds);

  /** Returns readable concrete instruments and their direct Inventory edit permission. */
  Map<Long, BookingItemAccess> getBookingItemAccess(Set<Long> instrumentIds, User user);

  /**
   * Returns concrete Instruments for Booking's safe relationship renderer, independent of Inventory
   * sharing. Callers must render them through Booking's allowlisted description.
   */
  Map<Long, Instrument> getBookingRelationshipTargets(Set<Long> instrumentIds);

  /**
   * Lists active concrete Instruments eligible for a new Booking configuration, ordered by name. A
   * {@code null} or blank query browses every eligible Instrument.
   */
  List<Instrument> searchEligibleBookingTargets(String query, int limit, User subject);

  /**
   * Finds active Instrument identifiers whose name, description, or readable immediate parent name
   * contains the supplied Booking catalogue query.
   */
  Set<Long> searchBookingCatalogueTargetIds(String query, User subject);
}
