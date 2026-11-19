package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.model.User;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ResourceRoleScheme;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Resolves which instruments count as bookable for the Inventory search "Bookable" filter. */
@Component
public class BookableInstrumentLookup {

  private @Autowired BookingConfigurationDao bookingConfigurationDao;
  private @Autowired BookingResourceRoleScheme bookingRoleScheme;
  private @Autowired FeatureFlagManager featureFlags;

  /**
   * Ids of instruments with an active, enabled Booking configuration the user can read. Empty when
   * Booking is disabled for the user. Requires an active transaction.
   *
   * @param user the searching user
   * @return possibly empty set of instrument db ids
   */
  public Set<Long> findReadableBookableInstrumentIds(User user) {
    if (!featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, user)) {
      return Set.of();
    }
    return bookingConfigurationDao.findBookableInstrumentIds(
        user, bookingRoleScheme.rolesWithCapability(ResourceRoleScheme.READ_RESOURCE_CAPABILITY));
  }
}
