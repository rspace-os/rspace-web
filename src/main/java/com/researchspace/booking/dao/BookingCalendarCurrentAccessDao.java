package com.researchspace.booking.dao;

import java.util.Set;

/**
 * Current, cache-bypassing Inventory authorization reads used while the permission gate is held.
 */
public interface BookingCalendarCurrentAccessDao {

  enum Access {
    DENIED,
    VIEWER,
    BOOKER,
    OWNER
  }

  /** Resolves one subject/instrument pair from locking scalar reads in the physical transaction. */
  Access resolve(Long subjectId, Long instrumentId);

  /**
   * Permanently revokes tokens that have lost read permission in the coalesced scope.
   *
   * @return number of removed token rows
   */
  int revokeDenied(Set<Long> userIds, Set<Long> instrumentIds, boolean all);
}
