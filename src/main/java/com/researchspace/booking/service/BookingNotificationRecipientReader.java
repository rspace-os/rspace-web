package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.booking.dao.BookingNotificationSubscriptionDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.FeatureFlagManager;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.hibernate.CacheMode;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Selects eligible recipients from one fresh, read-only database snapshot. */
@Service
public class BookingNotificationRecipientReader {

  private final BookingNotificationSubscriptionDao subscriptions;
  private final UserDao users;
  private final BookingItemPermissions permissions;
  private final BookingDisplayPreferencesManager displayPreferences;
  private final FeatureFlagManager featureFlags;
  private final SessionFactory sessionFactory;
  private final Clock institutionClock;

  public BookingNotificationRecipientReader(
      BookingNotificationSubscriptionDao subscriptions,
      UserDao users,
      BookingItemPermissions permissions,
      BookingDisplayPreferencesManager displayPreferences,
      FeatureFlagManager featureFlags,
      SessionFactory sessionFactory,
      @Qualifier(com.researchspace.booking.config.BookingTimeConfig.INSTITUTION_CLOCK)
          Clock institutionClock) {
    this.subscriptions = subscriptions;
    this.users = users;
    this.permissions = permissions;
    this.displayPreferences = displayPreferences;
    this.featureFlags = featureFlags;
    this.sessionFactory = sessionFactory;
    this.institutionClock = institutionClock;
  }

  /** Selects subscribers using a fresh eligibility snapshot. */
  @Transactional(
      propagation = Propagation.REQUIRES_NEW,
      isolation = Isolation.REPEATABLE_READ,
      readOnly = true)
  public List<BookingNotificationRecipient> selectRecipients(
      Long instrumentId, NotificationType event, Long actorId) {
    return selectRecipients(instrumentId, event, actorId, null);
  }

  /**
   * Selects all enabled subscribers who can currently read the target and want this event.
   *
   * <p>For cancellations by somebody other than the requester, the requester is added when they are
   * active, booking-enabled, and can still read the target. The requester is deliberately selected
   * without their event preference because the cancellation is about their booking.
   *
   * <p>The candidate query is the first consistent read and establishes the snapshot used by all
   * later feature-flag, permission, and display-default reads. Returned users are detached when
   * this REQUIRES_NEW transaction closes; their basic fields and preference collection are loaded
   * for notification creation and after-commit email delivery.
   */
  @Transactional(
      propagation = Propagation.REQUIRES_NEW,
      isolation = Isolation.REPEATABLE_READ,
      readOnly = true)
  public List<BookingNotificationRecipient> selectRecipients(
      Long instrumentId, NotificationType event, Long actorId, Long requesterId) {
    if (instrumentId == null || !BookingNotificationMessageFormatter.isBookingNotification(event)) {
      return List.of();
    }

    Session session = sessionFactory.getCurrentSession();
    session.setCacheMode(CacheMode.IGNORE);
    session.setDefaultReadOnly(true);

    // This unlocked query is deliberately the first SELECT in this transaction.
    List<com.researchspace.model.booking.BookableItemNotificationSubscription> candidates =
        subscriptions.findEnabledByInstrument(instrumentId);
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.replaceTarget(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, instrumentId));
    boolean includeRequester =
        NotificationType.NOTIFICATION_BOOKING_CANCELLED.equals(event)
            && requesterId != null
            && !Objects.equals(actorId, requesterId);
    User requester = includeRequester ? users.getSafeNull(requesterId).orElse(null) : null;

    List<BookingNotificationRecipient> selected = new ArrayList<>();
    Set<Long> selectedIds = new HashSet<>();
    for (var subscription : candidates) {
      User recipient = subscription.getUser();
      Long recipientId = recipient == null ? null : recipient.getId();
      if ((includeRequester && Objects.equals(requesterId, recipientId))
          || selectedIds.contains(recipientId)
          || !active(recipient)
          || Objects.equals(actorId, recipientId)
          || !featureFlags.isFeatureFlagEnabledInSnapshot(BOOKING_ENABLED, recipient)
          || !recipient.wantsNotificationFor(event)) {
        continue;
      }

      if (!permissions
          .resolve(configuration, recipient)
          .hasCapability(BookingResourceRoleScheme.READ_RESOURCE)) {
        continue;
      }

      selectedIds.add(recipient.getId());
      selected.add(snapshot(recipient, event));
    }

    if (includeRequester && eligibleRequester(requester, configuration)) {
      selected.add(snapshot(requester, event));
    }
    return List.copyOf(selected);
  }

  private boolean eligibleRequester(User recipient, BookingConfiguration configuration) {
    return active(recipient)
        && featureFlags.isFeatureFlagEnabledInSnapshot(BOOKING_ENABLED, recipient)
        && permissions
            .resolve(configuration, recipient)
            .hasCapability(BookingResourceRoleScheme.READ_RESOURCE);
  }

  private BookingNotificationRecipient snapshot(User recipient, NotificationType event) {
    Hibernate.initialize(recipient.getUserPreferences());
    // These preference reads are consumed after this transaction closes.
    recipient.wantsNotificationFor(event);
    recipient.getValueForPreference(Preference.BROADCAST_NOTIFICATIONS_BY_EMAIL).getValue();
    recipient.getId();
    recipient.getUsername();
    recipient.getFirstName();
    recipient.getLastName();
    recipient.getFullName();
    recipient.getEmail();
    recipient.isEnabled();

    var display = displayPreferences.resolveForNotificationSnapshot(recipient);
    ZoneId displayZone =
        BookingNotificationMessageFormatter.zoneFor(display, null, institutionClock.getZone());
    return new BookingNotificationRecipient(recipient, displayZone, display.timeFormat());
  }

  private static boolean active(User user) {
    return user != null && user.isEnabled() && !user.isAccountLocked();
  }
}
