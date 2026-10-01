package com.researchspace.booking.service;

import com.researchspace.dao.query.RsqlCollectionQuery;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingLocationResource;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.ResourceRequest;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the filter-only Booking {@code location} relationship against Inventory read access.
 *
 * <p>A bookable item's location is its immediate parent Container, which Booking does not store. A
 * location comparison is therefore replaced, in place, by a {@code target.value} comparison over
 * the configured items stored directly in the named Containers that the caller may read. A
 * Container the caller cannot read, and one that does not exist, both contribute no items, so the
 * result never discloses that an unreadable Container exists or what it holds.
 */
public interface BookingLocationFilterManager {

  /**
   * Returns {@code filter} with every {@code location} and {@code location.value} comparison
   * replaced by an equivalent target comparison, preserving the Boolean structure.
   *
   * @throws com.researchspace.model.collection.CollectionQueryException when a location comparison
   *     would name more items than one query may bind
   */
  FilterExpression resolveLocations(FilterExpression filter, User caller);

  /** As {@link #resolveLocations(FilterExpression, User)}, for a request's caller filter. */
  ResourceRequest resolveLocations(ResourceRequest request, User caller);

  /** A request with its location filters resolved, and a trusted restriction to AND with it. */
  record Resolved(ResourceRequest request, RsqlCollectionQuery.Predicate restriction) {}

  /**
   * As {@link #resolveLocations(ResourceRequest, User)}, except that each location comparison that
   * is a top-level conjunct of the caller filter becomes a correlated readable-parent restriction
   * instead of an item list, so it has no size limit. The rows it matches are the same. {@code
   * targetPath} is the configuration target reference in the query that applies the restriction,
   * such as {@code booking.bookingConfiguration.target} for events. A comparison nested in an OR
   * group is still resolved as a list.
   */
  Resolved resolveLocations(ResourceRequest request, User caller, String targetPath);

  /** Returns the readable locations among {@code ids}, for relationship expansion. */
  Map<Long, ApiV2BookingLocationResource.Location> findReadableLocations(
      Set<Long> ids, User caller);
}
