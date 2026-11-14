package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.model.User;
import com.researchspace.service.FeatureFlagManager;

/** Shared active-user and feature access check for Booking user operations. */
final class BookingUserAccess {

  private BookingUserAccess() {}

  static boolean hasAccess(FeatureFlagManager featureFlags, User subject, User actor) {
    return active(subject)
        && active(actor)
        && featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, subject);
  }

  private static boolean active(User user) {
    return user != null && user.isEnabled() && !user.isAccountLocked();
  }
}
