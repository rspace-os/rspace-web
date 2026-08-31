package com.researchspace.booking.service;

import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
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

  public BookingConfigurationTargetManagerImpl(InstrumentDao instrumentDao) {
    this.instrumentDao = instrumentDao;
  }

  @Override
  public List<BookingConfigurationTarget> search(String query, int limit, User subject) {
    if (subject == null || !subject.isEnabled()) {
      throw new AuthorizationException("errors.api.v2.authenticationRequired");
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
