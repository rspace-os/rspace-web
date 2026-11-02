package com.researchspace.booking.dao;

import com.researchspace.dao.GenericDao;
import com.researchspace.model.booking.BookableItemCalendarSubscription;
import java.util.List;
import java.util.Optional;

/** Persistence operations for bookable-item calendar subscriptions. */
public interface BookingCalendarSubscriptionDao
    extends GenericDao<BookableItemCalendarSubscription, Long> {

  /** Serializes item subscription creation with permission-change revocation until commit. */
  void lockPermissionFence();

  Optional<BookableItemCalendarSubscription> findByUserIdAndConfigurationId(
      Long userId, Long configurationId);

  /**
   * Flushes pending local changes and returns the current committed-or-local scalar row under a
   * write lock, bypassing the persistence context and an older repeatable-read snapshot.
   */
  Optional<CurrentItemCalendarSubscription> lockCurrent(Long userId, Long configurationId);

  /**
   * Conditionally rotates the locked current row using its token hash as an ETag.
   *
   * @throws jakarta.persistence.OptimisticLockException if the current hash no longer matches
   */
  CurrentItemCalendarSubscription rotateCurrent(
      CurrentItemCalendarSubscription current,
      String expectedTokenHash,
      String newTokenHash,
      String newRawToken,
      java.util.Date updatedAt);

  /** Re-reads and refreshes exactly one newly-created row after its insert has been flushed. */
  CurrentItemCalendarSubscription reconcileCreated(Long userId, Long configurationId);

  /** Finds all of one user's item subscriptions with their configurations loaded. */
  List<BookableItemCalendarSubscription> findByUserId(Long userId);

  /** Finds a subscription by its one-way token hash. */
  Optional<BookableItemCalendarSubscription> findByTokenHash(String tokenHash);

  /** Saves and flushes a subscription so uniqueness failures occur inside the manager call. */
  BookableItemCalendarSubscription saveAndFlush(BookableItemCalendarSubscription subscription);

  /** Removes one user's subscription and returns the number of rows removed. */
  int removeForUserAndConfiguration(Long userId, Long configurationId);

  /** Removes a subscription by identity without loading it into the current session. */
  int deleteById(Long subscriptionId);

  /** Removes all subscriptions for one configuration and returns the number of rows removed. */
  int deleteByConfigurationId(Long configurationId);
}
