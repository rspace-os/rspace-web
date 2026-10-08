package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import java.util.List;

/** Manages personal booking-notification preferences and instrument subscriptions. */
public interface BookingNotificationSubscriptionManager {

  /**
   * The caller's booking-notification choices.
   *
   * @param autoSubscribeOwnedItems whether newly owned bookable instruments start subscribed
   * @param notifyOnCreated the My Profile "booking created" notification preference
   * @param notifyOnCancelled the My Profile "booking cancelled" notification preference
   * @param emailDelivery the effective My Profile preference for emailing notifications; read-only
   */
  record Preferences(
      boolean autoSubscribeOwnedItems,
      boolean notifyOnCreated,
      boolean notifyOnCancelled,
      boolean emailDelivery) {}

  /** The editable subset of {@link Preferences}. */
  record PreferenceChanges(
      boolean autoSubscribeOwnedItems, boolean notifyOnCreated, boolean notifyOnCancelled) {}

  record Status(
      long configurationId,
      boolean enabled,
      long version,
      boolean createdEnabled,
      boolean cancelledEnabled,
      boolean emailEnabled) {}

  /**
   * Returns the current subject's default for future owned instruments, the booking event
   * preferences shared with My Profile, and the effective email delivery preference.
   */
  Preferences getPreferences(User subject, User actor);

  /**
   * Replaces the subject's future default and both booking event preferences. Existing
   * subscriptions and the email delivery preference are unchanged.
   */
  Preferences replacePreferences(PreferenceChanges changes, User subject, User actor);

  /** Reads one visible configuration's personal subscription and effective event delivery. */
  Status get(long configurationId, User subject, User actor);

  /** Changes one readable instrument subscription using its expected subscription version. */
  Status replace(
      long configurationId, boolean enabled, long expectedVersion, User subject, User actor);

  /** Reads personal subscriptions for a bounded set of visible configurations. */
  List<Status> getMany(List<Long> configurationIds, User subject, User actor);

  /** Atomically applies one explicit choice to a bounded set of readable configurations. */
  List<Status> replaceMany(List<Long> configurationIds, boolean enabled, User subject, User actor);

  /** Disables all of the caller's enabled subscriptions, including dormant former-owner rows. */
  int unsubscribeAll(User subject, User actor);

  /** Initializes the current owner's subscription from their personal default, only if absent. */
  void initializeForInstrument(Instrument instrument);
}
