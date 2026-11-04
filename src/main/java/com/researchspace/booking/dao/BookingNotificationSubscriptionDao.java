package com.researchspace.booking.dao;

import com.researchspace.dao.GenericDao;
import com.researchspace.model.booking.BookableItemNotificationSubscription;
import java.util.List;
import java.util.Optional;

/** Persistence operations for personal instrument booking-notification subscriptions. */
public interface BookingNotificationSubscriptionDao
    extends GenericDao<BookableItemNotificationSubscription, Long> {

  Optional<BookableItemNotificationSubscription> findByUserAndInstrument(
      Long userId, Long instrumentId);

  /**
   * Selects subscriber identities without hydrating associated users or changing their mutability.
   */
  List<Long> findEnabledUserIds(Long instrumentId);

  /** Loads one row with a current locking read after the instrument lock is held. */
  default Optional<BookableItemNotificationSubscription> findByUserAndInstrumentForUpdate(
      Long userId, Long instrumentId) {
    return findByUserAndInstrumentsForUpdate(userId, List.of(instrumentId)).stream().findFirst();
  }

  List<BookableItemNotificationSubscription> findByUserAndInstruments(
      Long userId, List<Long> instrumentIds);

  /** Loads rows with a current locking read after the requested instrument locks are held. */
  List<BookableItemNotificationSubscription> findByUserAndInstrumentsForUpdate(
      Long userId, List<Long> instrumentIds);

  List<Long> findInstrumentIdsByUser(Long userId);

  /** Updates enabled rows to disabled and increments their optimistic versions. */
  int unsubscribeEnabledForUser(Long userId, java.util.Date updatedAt);

  /** Resolves full Inventory read permission in set queries from the transaction snapshot. */
  java.util.Set<Long> findReadableUsers(Long instrumentId, java.util.Set<Long> userIds);

  /** Loads writable users and preferences without consulting the second-level cache. */
  List<com.researchspace.model.User> findRecipientUsers(java.util.Set<Long> userIds);

  /** Inserts a default if absent, preserving explicit choices and the caller's transaction. */
  void initialize(Long userId, Long instrumentId, boolean enabled, java.util.Date now);

  BookableItemNotificationSubscription saveAndFlush(
      BookableItemNotificationSubscription subscription);
}
