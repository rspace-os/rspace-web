package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finds safe, currently eligible Booking-configuration targets. */
public interface BookingConfigurationTargetManager {

  /** Returns an ownership-bounded, name-matched list of eligible concrete Instruments. */
  List<BookingConfigurationTarget> search(String query, int limit, User subject);

  /** Resolves concrete Instruments for Booking's internal relationship projection. */
  Map<Long, Instrument> resolveRelationshipTargets(Set<Long> instrumentIds);
}
