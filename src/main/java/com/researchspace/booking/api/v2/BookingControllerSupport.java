package com.researchspace.booking.api.v2;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.model.User;
import com.researchspace.service.FeatureFlagManager;
import jakarta.ws.rs.NotFoundException;

/** Shared concealed read-availability gate for Booking endpoints. */
public final class BookingControllerSupport {

  private BookingControllerSupport() {}

  public static void requireEnabled(FeatureFlagManager featureFlags, User subject) {
    if (!featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, subject)) {
      throw new NotFoundException();
    }
  }
}
