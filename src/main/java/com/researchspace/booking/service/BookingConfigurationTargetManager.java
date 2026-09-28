package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finds safe, currently eligible Booking-configuration targets. */
public interface BookingConfigurationTargetManager {

  /**
   * Returns an ownership-bounded list of eligible concrete Instruments ordered by name.
   *
   * @param query a name fragment or exact global ID, or {@code null} to browse all eligible targets
   * @param limit the maximum number of targets
   * @param subject the effective caller
   */
  List<BookingConfigurationTarget> search(String query, int limit, User subject);

  /** Resolves only concrete Instruments currently readable by the relationship caller. */
  Map<Long, Instrument> resolveRelationshipTargets(Set<Long> instrumentIds, User caller);
}
