package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.service.FeatureFlagManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.CacheMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Authorizes personal subscription writes against committed access after instrument locking. */
@Component
@RequiredArgsConstructor
public class BookingFreshSubscriptionAccessReader {
  private final UserDao users;
  private final BookingItemPermissions permissions;
  private final FeatureFlagManager featureFlags;
  private final SessionFactory sessionFactory;

  /** A later revocation leaves the saved choice dormant; this read acquires no permission fence. */
  @Transactional(
      propagation = Propagation.REQUIRES_NEW,
      isolation = Isolation.REPEATABLE_READ,
      readOnly = true)
  public boolean canReadAll(List<BookableTargetReference> targets, Long subjectId) {
    Session session = sessionFactory.getCurrentSession();
    CacheMode previous = session.getCacheMode();
    session.setCacheMode(CacheMode.IGNORE);
    try {
      User subject = users.getSafeNull(subjectId).orElse(null);
      if (subject == null
          || !subject.isEnabled()
          || subject.isAccountLocked()
          || !featureFlags.isFeatureFlagEnabledInSnapshot(BOOKING_ENABLED, subject)) {
        return false;
      }
      for (BookableTargetReference target : targets) {
        BookingConfiguration configuration = new BookingConfiguration();
        configuration.replaceTarget(target);
        if (!permissions
            .resolve(configuration, subject)
            .hasCapability(BookingResourceRoleScheme.READ_RESOURCE)) {
          return false;
        }
      }
      return true;
    } finally {
      session.setCacheMode(previous);
    }
  }
}
