package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.FeatureFlagManager;
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

  public BookingConfigurationTargetManagerImpl(
      InstrumentDao instrumentDao, FeatureFlagManager featureFlags) {
    this.instrumentDao = instrumentDao;
    this.featureFlags = featureFlags;
  }

  @Override
  public List<BookingConfigurationTarget> search(String query, int limit, User subject) {
    if (subject == null || !subject.isEnabled()) {
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
  public Map<Long, Instrument> resolveRelationshipTargets(Set<Long> instrumentIds) {
    return instrumentDao.getBookingRelationshipTargets(instrumentIds);
  }
}
