package com.researchspace.booking.service;

import com.researchspace.api.ConcurrentChangeClassifier;
import com.researchspace.booking.dao.BookingCalendarPermissionGateDao;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.BookingNotificationSubscriptionDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.featureflags.FeatureFlags;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.booking.BookableItemNotificationSubscription;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.CollectionMutationException;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional personal operations for instrument booking-notification subscriptions. */
@Service("bookingNotificationSubscriptionManager")
@Transactional
public class BookingNotificationSubscriptionManagerImpl
    implements BookingNotificationSubscriptionManager {

  private static final Logger log =
      LoggerFactory.getLogger(BookingNotificationSubscriptionManagerImpl.class);

  private static final int MAX_BULK_ITEMS = 100;
  private static final long UNSAVED_VERSION = -1;

  private final BookingNotificationSubscriptionDao subscriptions;
  private final BookingConfigurationDao configurations;
  private final InstrumentDao instruments;
  private final BookingItemPermissions itemPermissions;
  private final UserManager userManager;
  private final FeatureFlagManager featureFlags;
  private final BookingFreshSubscriptionAccessReader freshAccess;
  private final BookingCalendarPermissionGateDao permissionGate;

  public BookingNotificationSubscriptionManagerImpl(
      @Qualifier("bookingNotificationSubscriptionDao")
          BookingNotificationSubscriptionDao subscriptions,
      @Qualifier("bookingConfigurationDao") BookingConfigurationDao configurations,
      InstrumentDao instruments,
      BookingItemPermissions itemPermissions,
      UserManager userManager,
      FeatureFlagManager featureFlags,
      BookingFreshSubscriptionAccessReader freshAccess,
      BookingCalendarPermissionGateDao permissionGate) {
    this.subscriptions = subscriptions;
    this.configurations = configurations;
    this.instruments = instruments;
    this.itemPermissions = itemPermissions;
    this.userManager = userManager;
    this.featureFlags = featureFlags;
    this.freshAccess = freshAccess;
    this.permissionGate = permissionGate;
  }

  @Override
  @Transactional(readOnly = true)
  public Preferences getPreferences(User subject, User actor) {
    requireAccess(subject, actor);
    return preferences(subject);
  }

  @Override
  public Preferences replacePreferences(PreferenceChanges changes, User subject, User actor) {
    requireAccess(subject, actor);
    Objects.requireNonNull(changes, "Booking notification preference changes");
    setPreference(
        subject,
        Preference.BOOKING_AUTO_SUBSCRIBE_NOTIFICATIONS,
        changes.autoSubscribeOwnedItems());
    setPreference(subject, Preference.BOOKING_NOTIFY_OWN_ACTIONS, changes.notifyOwnActions());
    setPreference(subject, Preference.NOTIFICATION_BOOKING_CREATED_PREF, changes.notifyOnCreated());
    setPreference(
        subject, Preference.NOTIFICATION_BOOKING_CANCELLED_PREF, changes.notifyOnCancelled());
    return new Preferences(
        changes.autoSubscribeOwnedItems(),
        changes.notifyOwnActions(),
        changes.notifyOnCreated(),
        changes.notifyOnCancelled(),
        preferenceEnabled(subject, Preference.BROADCAST_NOTIFICATIONS_BY_EMAIL));
  }

  private Preferences preferences(User subject) {
    return new Preferences(
        autoSubscribe(subject),
        preferenceEnabled(subject, Preference.BOOKING_NOTIFY_OWN_ACTIONS),
        preferenceEnabled(subject, Preference.NOTIFICATION_BOOKING_CREATED_PREF),
        preferenceEnabled(subject, Preference.NOTIFICATION_BOOKING_CANCELLED_PREF),
        preferenceEnabled(subject, Preference.BROADCAST_NOTIFICATIONS_BY_EMAIL));
  }

  private void setPreference(User subject, Preference preference, boolean value) {
    userManager.setPreference(preference, Boolean.toString(value), subject.getUsername());
  }

  @Override
  @Transactional(readOnly = true)
  public Status get(long configurationId, User subject, User actor) {
    return getMany(List.of(configurationId), subject, actor).get(0);
  }

  @Override
  public Status replace(
      long configurationId, boolean enabled, String expectedTag, User subject, User actor) {
    return subscriptionWrite(
        () -> replaceLocked(configurationId, enabled, expectedTag, subject, actor));
  }

  private Status replaceLocked(
      long configurationId, boolean enabled, String expectedTag, User subject, User actor) {
    permissionGate.lockShared();
    requireAccess(subject, actor);
    ResolvedItem item = resolveItems(List.of(configurationId), subject, true).get(0);
    BookableItemNotificationSubscription current =
        (expectedTag.startsWith("\"vabsent-")
                ? subscriptions.findByUserAndInstrument(subject.getId(), item.instrument().getId())
                : subscriptions.findByUserAndInstrumentForUpdate(
                    subject.getId(), item.instrument().getId()))
            .orElse(null);
    if (!BookingNotificationSubscriptionManager.etag(status(item, current, subject))
        .equals(expectedTag)) {
      throw new BookingNotificationSubscriptionConflictException();
    }
    Date now = new Date();
    current = upsert(current, subject, item.instrument(), enabled, now);
    BookableItemNotificationSubscription saved = subscriptions.saveAndFlush(current);
    return status(item, saved, subject);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Status> getMany(List<Long> configurationIds, User subject, User actor) {
    requireAccess(subject, actor);
    List<ResolvedItem> items = resolveItems(configurationIds, subject, false);
    Map<Long, BookableItemNotificationSubscription> savedByInstrument =
        subscriptions
            .findByUserAndInstruments(
                subject.getId(),
                items.stream().map(item -> item.instrument().getId()).distinct().toList())
            .stream()
            .collect(
                Collectors.toMap(
                    subscription -> subscription.getInstrument().getId(), value -> value));
    return items.stream()
        .map(item -> status(item, savedByInstrument.get(item.instrument().getId()), subject))
        .toList();
  }

  @Override
  public int unsubscribeAll(User subject, User actor) {
    return subscriptionWrite(
        () -> {
          requireAccess(subject, actor);
          // The instrument lock serializes this update with preference changes and ownership
          // transfers.
          for (Long instrumentId :
              new TreeSet<>(subscriptions.findInstrumentIdsByUser(subject.getId()))) {
            instruments.lockById(instrumentId);
          }
          return subscriptions.unsubscribeEnabledForUser(subject.getId(), new Date());
        });
  }

  @Override
  public void initializeForInstrument(Instrument instrument) {
    if (instrument == null
        || instrument.getId() == null
        || instrument.isDeleted()
        || instrument.isTemplate()
        || instrument.getOwner() == null
        || instrument.getOwner().getId() == null) {
      return;
    }
    User owner = instrument.getOwner();
    subscriptions.initialize(owner.getId(), instrument.getId(), autoSubscribe(owner), new Date());
  }

  private <T> T subscriptionWrite(Supplier<T> mutation) {
    try {
      return mutation.get();
    } catch (RuntimeException failure) {
      if (!ConcurrentChangeClassifier.isConcurrentChange(failure)) throw failure;
      log.warn("Concurrent booking notification subscription write", failure);
      throw new BookingNotificationSubscriptionConflictException();
    }
  }

  private List<ResolvedItem> resolveItems(
      List<Long> configurationIds, User subject, boolean lockInstruments) {
    if (configurationIds == null || configurationIds.isEmpty()) {
      throw new IllegalArgumentException("At least one booking configuration is required");
    }
    List<Long> ids = configurationIds.stream().distinct().toList();
    if (ids.size() > MAX_BULK_ITEMS) {
      throw new CollectionMutationException(CollectionMutationException.Reason.BULK_LIMIT);
    }
    List<BookingConfiguration> items =
        ids.stream()
            .map(id -> configurations.getSafeNull(id).orElseThrow(NotFoundException::new))
            .toList();
    Map<Long, ResolvedResourceAccess> accessById = itemPermissions.resolveAll(items, subject);
    for (BookingConfiguration item : items) {
      if (!accessById
          .getOrDefault(item.getId(), ResolvedResourceAccess.none())
          .hasCapability(BookingResourceRoleScheme.READ_RESOURCE)) {
        throw new NotFoundException();
      }
    }
    for (BookingConfiguration item : items) {
      if (item.getTarget() == null
          || item.getTarget().type() != BookableTargetType.INSTRUMENT
          || item.getTarget().id() == null) {
        throw new NotFoundException();
      }
    }

    TreeSet<Long> targetIds =
        items.stream()
            .map(item -> item.getTarget().id())
            .collect(Collectors.toCollection(TreeSet::new));
    Map<Long, Instrument> instrumentsById = new LinkedHashMap<>();
    for (Long targetId : targetIds) {
      Instrument instrument =
          (lockInstruments ? instruments.lockById(targetId) : instruments.getSafeNull(targetId))
              .filter(target -> !target.isDeleted() && !target.isTemplate())
              .orElseThrow(NotFoundException::new);
      instrumentsById.put(targetId, instrument);
    }

    if (lockInstruments
        && !freshAccess.canReadAll(
            items.stream().map(BookingConfiguration::getTarget).toList(), subject.getId())) {
      throw new NotFoundException();
    }
    List<ResolvedItem> resolved = new ArrayList<>();
    for (BookingConfiguration item : items) {
      Instrument instrument = instrumentsById.get(item.getTarget().id());
      resolved.add(new ResolvedItem(item, instrument));
    }
    return resolved;
  }

  private Status status(
      ResolvedItem item, BookableItemNotificationSubscription subscription, User subject) {
    boolean enabled = subscription != null && subscription.isEnabled();
    boolean createdEnabled =
        enabled && preferenceEnabled(subject, Preference.NOTIFICATION_BOOKING_CREATED_PREF);
    boolean cancelledEnabled =
        enabled && preferenceEnabled(subject, Preference.NOTIFICATION_BOOKING_CANCELLED_PREF);
    boolean emailEnabled =
        (createdEnabled || cancelledEnabled)
            && preferenceEnabled(subject, Preference.BROADCAST_NOTIFICATIONS_BY_EMAIL);
    return new Status(
        item.configuration().getId(),
        enabled,
        subscription == null ? UNSAVED_VERSION : subscription.getVersion(),
        createdEnabled,
        cancelledEnabled,
        emailEnabled);
  }

  private boolean autoSubscribe(User user) {
    UserPreference preference =
        userManager.getPreferenceForUser(user, Preference.BOOKING_AUTO_SUBSCRIBE_NOTIFICATIONS);
    return preference.getValueAsBoolean();
  }

  private boolean preferenceEnabled(User user, Preference preference) {
    return userManager.getPreferenceForUser(user, preference).getValueAsBoolean();
  }

  private static BookableItemNotificationSubscription upsert(
      BookableItemNotificationSubscription current,
      User subject,
      Instrument instrument,
      boolean enabled,
      Date now) {
    if (current == null) {
      return new BookableItemNotificationSubscription(subject, instrument, enabled, now);
    }
    current.setEnabled(enabled);
    current.setUpdatedAt(now);
    return current;
  }

  private void requireAccess(User subject, User actor) {
    if (subject == null
        || actor == null
        || !subject.isEnabled()
        || !actor.isEnabled()
        || subject.isAccountLocked()
        || actor.isAccountLocked()) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
    if (!featureFlags.isFeatureFlagEnabled(FeatureFlags.BOOKING_ENABLED, subject)) {
      throw new NotFoundException();
    }
  }

  private record ResolvedItem(BookingConfiguration configuration, Instrument instrument) {}
}
