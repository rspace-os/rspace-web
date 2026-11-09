package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.booking.config.BookingTimeConfig;
import com.researchspace.booking.dao.BookingNotificationSubscriptionDao;
import com.researchspace.model.User;
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
import java.util.stream.Collectors;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Selects eligible recipients in the booking transaction. */
@Service
public class BookingNotificationRecipientReader {

  private final BookingNotificationSubscriptionDao subscriptions;
  private final BookingDisplayPreferencesManager displayPreferences;
  private final FeatureFlagManager featureFlags;
  private final Clock institutionClock;

  public BookingNotificationRecipientReader(
      BookingNotificationSubscriptionDao subscriptions,
      BookingDisplayPreferencesManager displayPreferences,
      FeatureFlagManager featureFlags,
      @Qualifier(BookingTimeConfig.INSTITUTION_CLOCK) Clock institutionClock) {
    this.subscriptions = subscriptions;
    this.displayPreferences = displayPreferences;
    this.featureFlags = featureFlags;
    this.institutionClock = institutionClock;
  }

  /** Selects subscribers using the booking transaction snapshot. */
  @Transactional
  public List<BookingNotificationRecipient> selectRecipients(
      Long instrumentId, NotificationType event, Long actorId) {
    return selectRecipients(instrumentId, event, actorId, null);
  }

  /**
   * Selects enabled subscribers who can read the target and want this event. Actors receive their
   * own events unless their snapshot preference disables own-action notifications.
   *
   * <p>For cancellations or time changes by somebody other than the requester, the requester is
   * added when they are active, Booking-enabled, and can still read the target. The requester is
   * selected without their event preference because the cancellation is about their booking.
   *
   * <p>Eligibility uses the booking transaction's snapshot. Notification rows are persisted on the
   * same connection and roll back with the booking.
   */
  @Transactional
  public List<BookingNotificationRecipient> selectRecipients(
      Long instrumentId, NotificationType event, Long actorId, Long requesterId) {
    if (instrumentId == null || !BookingNotificationMessageFormatter.isBookingNotification(event)) {
      return List.of();
    }

    List<Long> candidates = subscriptions.findEnabledUserIds(instrumentId);
    boolean includeRequester =
        (NotificationType.NOTIFICATION_BOOKING_CANCELLED.equals(event)
                || NotificationType.NOTIFICATION_BOOKING_CHANGED.equals(event))
            && requesterId != null
            && !Objects.equals(actorId, requesterId);
    Set<Long> candidateIds = new HashSet<>(candidates);
    if (includeRequester) candidateIds.add(requesterId);
    var usersById =
        subscriptions.findRecipientUsers(candidateIds).stream()
            .collect(Collectors.toMap(User::getId, user -> user));
    Set<Long> enabled = featureFlags.enabledUsersInSnapshot(BOOKING_ENABLED, candidateIds);
    Set<Long> readable = subscriptions.findReadableUsers(instrumentId, candidateIds);
    User requester = usersById.get(requesterId);
    var displays = displayPreferences.resolveForNotificationSnapshots(usersById.values());

    List<BookingNotificationRecipient> selected = new ArrayList<>();
    Set<Long> selectedIds = new HashSet<>();
    for (Long recipientId : candidates) {
      User recipient = usersById.get(recipientId);
      if ((includeRequester && Objects.equals(requesterId, recipientId))
          || selectedIds.contains(recipientId)
          || recipient == null
          || (Objects.equals(actorId, recipientId)
              && !recipient
                  .getValueForPreference(Preference.BOOKING_NOTIFY_OWN_ACTIONS)
                  .getValueAsBoolean())
          || !(active(recipient) && enabled.contains(recipientId) && readable.contains(recipientId))
          || !recipient.wantsNotificationFor(event)) {
        continue;
      }

      selectedIds.add(recipientId);
      selected.add(snapshot(recipient, event, false, displays.get(recipientId)));
    }

    if (includeRequester
        && active(requester)
        && enabled.contains(requesterId)
        && readable.contains(requesterId)) {
      selected.add(snapshot(requester, event, true, displays.get(requesterId)));
    }
    return List.copyOf(selected);
  }

  private BookingNotificationRecipient snapshot(
      User recipient,
      NotificationType event,
      boolean requester,
      BookingDisplayPreferencesManager.ResolvedBookingDisplayPreferences display) {
    Hibernate.initialize(recipient);
    Hibernate.initialize(recipient.getUserPreferences());
    // These preference reads are consumed after this transaction closes.
    if (!requester) {
      recipient.wantsNotificationFor(event);
    }
    recipient.getValueForPreference(Preference.BOOKING_NOTIFY_OWN_ACTIONS).getValue();
    recipient.getValueForPreference(Preference.BROADCAST_NOTIFICATIONS_BY_EMAIL).getValue();
    ZoneId displayZone =
        BookingNotificationMessageFormatter.zoneFor(display, null, institutionClock.getZone());
    return new BookingNotificationRecipient(
        recipient, displayZone, display.timeFormat(), requester);
  }

  private static boolean active(User user) {
    return user != null && user.isEnabled() && !user.isAccountLocked();
  }
}
