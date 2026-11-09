package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.dao.BookingInstrumentQueryDao;
import com.researchspace.model.User;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional implementation of the bounded Booking target directory. */
@Service
@Transactional(readOnly = true)
public class BookingConfigurationTargetManagerImpl implements BookingConfigurationTargetManager {

  private final BookingInstrumentQueryDao instrumentDao;
  private final FeatureFlagManager featureFlags;
  private final InventoryPermissionUtils inventoryPermissions;

  public BookingConfigurationTargetManagerImpl(
      BookingInstrumentQueryDao instrumentDao,
      FeatureFlagManager featureFlags,
      InventoryPermissionUtils inventoryPermissions) {
    this.instrumentDao = instrumentDao;
    this.featureFlags = featureFlags;
    this.inventoryPermissions = inventoryPermissions;
  }

  @Override
  public List<BookingConfigurationTarget> search(String query, int limit, User subject) {
    if (subject == null || !subject.isEnabled() || subject.isAccountLocked()) {
      throw new AuthorizationException("errors.api.v2.authenticationRequired");
    }
    if (!featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, subject)) {
      throw new NotFoundException();
    }
    return instrumentDao.searchEligibleBookingTargets(query, limit, subject).stream()
        .map(
            instrument ->
                new BookingConfigurationTarget(
                    instrument.getId(),
                    instrument.getGlobalIdentifier(),
                    instrument.getName(),
                    instrument.isDeleted()))
        .toList();
  }
}
