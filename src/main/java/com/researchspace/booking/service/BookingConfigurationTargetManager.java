package com.researchspace.booking.service;

import com.researchspace.model.User;
import java.util.List;

/** Finds safe, currently eligible Booking-configuration targets. */
public interface BookingConfigurationTargetManager {

  /** Returns an ownership-bounded, name-matched list of eligible concrete Instruments. */
  List<BookingConfigurationTarget> search(String query, int limit, User subject);
}
