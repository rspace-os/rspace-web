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

  /** Loads enabled subscribers with their user preference collections in one query. */
  List<BookableItemNotificationSubscription> findEnabledByInstrument(Long instrumentId);

  /** Loads one row with a current locking read after the instrument lock is held. */
  Optional<BookableItemNotificationSubscription> findByUserAndInstrumentForUpdate(
      Long userId, Long instrumentId);

  List<BookableItemNotificationSubscription> findByUserAndInstruments(
      Long userId, List<Long> instrumentIds);

  /** Loads rows with a current locking read after the requested instrument locks are held. */
  List<BookableItemNotificationSubscription> findByUserAndInstrumentsForUpdate(
      Long userId, List<Long> instrumentIds);

  List<Long> findInstrumentIdsByUser(Long userId);

  /** Updates enabled rows to disabled and increments their optimistic versions. */
  int unsubscribeEnabledForUser(Long userId, java.util.Date updatedAt);

  BookableItemNotificationSubscription saveAndFlush(
      BookableItemNotificationSubscription subscription);
}
