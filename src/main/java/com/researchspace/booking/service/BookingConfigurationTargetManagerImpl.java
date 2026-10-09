package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional implementation of the bounded Booking target directory. */
@Service
@Transactional(readOnly = true)
public class BookingConfigurationTargetManagerImpl implements BookingConfigurationTargetManager {

  private final InstrumentDao instrumentDao;
  private final FeatureFlagManager featureFlags;
  private final InventoryPermissionUtils inventoryPermissions;

  public BookingConfigurationTargetManagerImpl(
      InstrumentDao instrumentDao,
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

  @Override
  public Map<Long, Instrument> resolveRelationshipTargets(Set<Long> instrumentIds, User caller) {
    if (caller == null || !caller.isEnabled() || caller.isAccountLocked()) {
      return Map.of();
    }
    return instrumentDao.getBookingRelationshipTargets(instrumentIds).entrySet().stream()
        .filter(entry -> !entry.getValue().isDeleted())
        .filter(entry -> inventoryPermissions.canUserReadInventoryRecord(entry.getValue(), caller))
        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
