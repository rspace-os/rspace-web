package com.researchspace.dao;

import com.researchspace.model.User;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentParentLocationSummary;
import com.researchspace.model.inventory.InstrumentReadSummary;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Booking-specific instrument queries; callers must retain an active transaction. */
public interface BookingInstrumentQueryDao {

  /**
   * Returns one database-paginated page of immediate parent Containers that are both readable in
   * Inventory and attached to an active, enabled Booking configuration readable by the caller.
   */
  ResourcePage<InstrumentParentLocationSummary> getBookingCatalogueLocations(
      String query, int page, int limit, User caller, Set<String> readableRoleKeys);

  /** Returns only the safe scalar fields Booking may reveal, independent of Inventory sharing. */
  Map<Long, InstrumentReadSummary> getBookingSummaries(Set<Long> instrumentIds);

  /**
   * Returns concrete Instruments for Booking's safe relationship renderer, independent of Inventory
   * sharing. Callers must render them through Booking's allowlisted description.
   */
  Map<Long, Instrument> getBookingRelationshipTargets(Set<Long> instrumentIds);

  /** Searches active concrete Instruments eligible for a new Booking configuration. */
  List<Instrument> searchEligibleBookingTargets(String query, int limit, User subject);

  /**
   * Finds active Instrument identifiers whose name, description, or readable immediate parent name
   * contains the supplied Booking catalogue query.
   */
  Set<Long> searchBookingCatalogueTargetIds(String query, User subject);
}
