package com.researchspace.booking.dao;

import com.researchspace.dao.GenericDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.UserBookingCalendarSubscription;
import java.util.Optional;

/** Persistence operations for user-wide booking calendar subscriptions. */
public interface UserBookingCalendarSubscriptionDao
    extends GenericDao<UserBookingCalendarSubscription, Long> {

  /** Finds the subscription owned by one user. */
  Optional<UserBookingCalendarSubscription> findByUserId(Long userId);

  /** Finds a subscription by its one-way token hash. */
  Optional<UserBookingCalendarSubscription> findByTokenHash(String tokenHash);

  /** Locks the durable owner row to serialize optional-subscription state transitions. */
  User lockUser(Long userId);

  /** Saves and flushes a subscription so uniqueness failures occur inside the manager call. */
  UserBookingCalendarSubscription saveAndFlush(UserBookingCalendarSubscription subscription);

  /** Removes one user's subscription and returns the number of rows removed. */
  int removeForUser(Long userId);
}
