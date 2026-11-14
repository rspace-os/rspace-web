package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.booking.dao.BookingCalendarCurrentAccessDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.service.FeatureFlagManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Authorizes personal subscription writes with current reads on the caller's connection. */
@Component
@RequiredArgsConstructor
public class BookingFreshSubscriptionAccessReader {
  private final UserDao users;
  private final BookingCalendarCurrentAccessDao currentAccess;
  private final FeatureFlagManager featureFlags;

  /**
   * The caller holds the shared gate and instrument locks. Scalar locking reads bypass both the
   * repeatable-read snapshot and managed authorization entities, including cached account state.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean canReadAll(List<BookableTargetReference> targets, Long subjectId) {
    User subject = users.getSafeNull(subjectId).orElse(null);
    if (subject == null
        || !featureFlags.isFeatureFlagEnabledWithCurrentReads(BOOKING_ENABLED, subject)) {
      return false;
    }
    for (BookableTargetReference target : targets) {
      if (target.type() != BookableTargetType.INSTRUMENT
          || currentAccess.resolveForRead(subjectId, target.id())
              == BookingCalendarCurrentAccessDao.Access.DENIED) {
        return false;
      }
    }
    return true;
  }
}
