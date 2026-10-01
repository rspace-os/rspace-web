package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.BookingNotificationSubscriptionDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.booking.BookableItemNotificationSubscription;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.CollectionMutationException;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.UserManager;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional personal operations for instrument booking-notification subscriptions. */
@Service("bookingNotificationSubscriptionManager")
@Transactional
public class BookingNotificationSubscriptionManagerImpl
    implements BookingNotificationSubscriptionManager {

  private static final int MAX_BULK_ITEMS = 100;
  private static final long UNSAVED_VERSION = -1;

  private final BookingNotificationSubscriptionDao subscriptions;
  private final BookingConfigurationDao configurations;
  private final InstrumentDao instruments;
  private final BookingItemPermissions itemPermissions;
  private final UserManager userManager;
  private final FeatureFlagManager featureFlags;
  private final BookingFreshSubscriptionAccessReader freshAccess;

  public BookingNotificationSubscriptionManagerImpl(
      @Qualifier("bookingNotificationSubscriptionDao")
          BookingNotificationSubscriptionDao subscriptions,
      @Qualifier("bookingConfigurationDao") BookingConfigurationDao configurations,
      InstrumentDao instruments,
      BookingItemPermissions itemPermissions,
      UserManager userManager,
      FeatureFlagManager featureFlags,
      BookingFreshSubscriptionAccessReader freshAccess) {
    this.subscriptions = subscriptions;
    this.configurations = configurations;
    this.instruments = instruments;
    this.itemPermissions = itemPermissions;
    this.userManager = userManager;
    this.featureFlags = featureFlags;
    this.freshAccess = freshAccess;
  }

  @Override
  @Transactional(readOnly = true)
  public Preferences getPreferences(User subject, User actor) {
    requireAccess(subject, actor);
    return new Preferences(autoSubscribe(subject));
  }

  @Override
  public Preferences replacePreferences(boolean autoSubscribeOwnedItems, User subject, User actor) {
    requireAccess(subject, actor);
    userManager.setPreference(
        Preference.BOOKING_AUTO_SUBSCRIBE_NOTIFICATIONS,
        Boolean.toString(autoSubscribeOwnedItems),
        subject.getUsername());
    return new Preferences(autoSubscribeOwnedItems);
  }

  @Override
  @Transactional(readOnly = true)
  public Status get(long configurationId, User subject, User actor) {
    return getMany(List.of(configurationId), subject, actor).get(0);
  }

  @Override
  public Status replace(
      long configurationId, boolean enabled, long expectedVersion, User subject, User actor) {
    requireAccess(subject, actor);
    if (expectedVersion < UNSAVED_VERSION) {
      throw new IllegalArgumentException("Invalid booking notification subscription version");
    }
    ResolvedItem item = resolveItems(List.of(configurationId), subject, true).get(0);
    BookableItemNotificationSubscription current =
        subscriptions
            .findByUserAndInstrumentForUpdate(subject.getId(), item.instrument().getId())
            .orElse(null);
    if (expectedVersion == UNSAVED_VERSION
        ? current != null
        : current == null || current.getVersion() != expectedVersion) {
      throw new BookingNotificationSubscriptionConflictException();
    }
    Date now = new Date();
    if (current == null) {
      current = new BookableItemNotificationSubscription(subject, item.instrument(), enabled, now);
    } else {
      current.setEnabled(enabled);
      current.setUpdatedAt(now);
    }
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
                java.util.stream.Collectors.toMap(
                    subscription -> subscription.getInstrument().getId(), value -> value));
    return items.stream()
        .map(item -> status(item, savedByInstrument.get(item.instrument().getId()), subject))
        .toList();
  }

  @Override
  public List<Status> replaceMany(
      List<Long> configurationIds, boolean enabled, User subject, User actor) {
    requireAccess(subject, actor);
    List<ResolvedItem> items = resolveItems(configurationIds, subject, true);
    List<Long> instrumentIds =
        items.stream().map(item -> item.instrument().getId()).distinct().toList();
    Map<Long, BookableItemNotificationSubscription> savedByInstrument =
        subscriptions.findByUserAndInstrumentsForUpdate(subject.getId(), instrumentIds).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    subscription -> subscription.getInstrument().getId(), value -> value));
    Date now = new Date();
    for (ResolvedItem item : items) {
      BookableItemNotificationSubscription subscription =
          savedByInstrument.get(item.instrument().getId());
      if (subscription == null) {
        subscription =
            new BookableItemNotificationSubscription(subject, item.instrument(), enabled, now);
      } else {
        subscription.setEnabled(enabled);
        subscription.setUpdatedAt(now);
      }
      subscriptions.saveAndFlush(subscription);
      savedByInstrument.put(item.instrument().getId(), subscription);
    }
    return items.stream()
        .map(item -> status(item, savedByInstrument.get(item.instrument().getId()), subject))
        .toList();
  }

  @Override
  public int unsubscribeAll(User subject, User actor) {
    requireAccess(subject, actor);
    // The instrument lock serializes this update with preference changes and ownership transfers.
    for (Long instrumentId :
        new TreeSet<>(subscriptions.findInstrumentIdsByUser(subject.getId()))) {
      instruments.lockById(instrumentId);
    }
    return subscriptions.unsubscribeEnabledForUser(subject.getId(), new Date());
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
    if (subscriptions
        .findByUserAndInstrumentForUpdate(owner.getId(), instrument.getId())
        .isPresent()) {
      return;
    }
    subscriptions.saveAndFlush(
        new BookableItemNotificationSubscription(
            owner, instrument, autoSubscribe(owner), new Date()));
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
    Map<Long, com.researchspace.service.resourceaccess.ResolvedResourceAccess> accessById =
        itemPermissions.resolveAll(items, subject);
    for (BookingConfiguration item : items) {
      if (!accessById
          .getOrDefault(
              item.getId(), com.researchspace.service.resourceaccess.ResolvedResourceAccess.none())
          .hasCapability(BookingResourceRoleScheme.READ_RESOURCE)) {
        throw new NotFoundException();
      }
    }
    for (BookingConfiguration item : items) {
      if (item.getTarget() == null
          || item.getTarget().type()
              != com.researchspace.model.booking.BookableTargetType.INSTRUMENT
          || item.getTarget().id() == null) {
        throw new NotFoundException();
      }
    }

    TreeSet<Long> targetIds =
        items.stream()
            .map(item -> item.getTarget().id())
            .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
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

  private void requireAccess(User subject, User actor) {
    if (!active(subject)
        || !active(actor)
        || !featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, subject)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }

  private static boolean active(User user) {
    return user != null && user.isEnabled() && !user.isAccountLocked();
  }

  private record ResolvedItem(BookingConfiguration configuration, Instrument instrument) {}
}
