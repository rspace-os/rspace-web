package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.booking.service.BookingCalendarManagerImpl.BookingCalendarNotFoundException;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import org.apache.shiro.authz.AuthorizationException;

/** Shared authorization checks for subscription lifecycles and downloads. */
final class BookingCalendarAccessChecks {

  static boolean hasCapability(
      ResourceAccessManager accessManager,
      BookingConfiguration configuration,
      User subject,
      String capability) {
    ResolvedResourceAccess access =
        accessManager.resolve(configuration.getResourceAccess(), subject);
    return access.hasCapability(capability);
  }

  static void requireFeatureRead(FeatureFlagManager featureFlags, User subject) {
    requireActiveUser(subject);
    if (!featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, subject)) {
      throw new BookingCalendarNotFoundException();
    }
  }

  static void requireActiveUser(User user) {
    if (!BookingDisplayPreferencesManagerImpl.active(user)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }
}
