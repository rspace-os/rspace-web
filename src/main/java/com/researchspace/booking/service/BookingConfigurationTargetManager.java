package com.researchspace.booking.service;

import com.researchspace.model.User;
import java.util.List;

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
}
