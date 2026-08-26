package com.researchspace.booking.dao;

import com.researchspace.dao.GenericDao;
import com.researchspace.model.booking.BookableItemCalendarSubscription;
import java.util.Optional;

/** Persistence operations for bookable-item calendar subscriptions. */
public interface BookingCalendarSubscriptionDao
    extends GenericDao<BookableItemCalendarSubscription, Long> {

  /** Finds one user's subscription for one booking configuration. */
  Optional<BookableItemCalendarSubscription> findByUserIdAndConfigurationId(
      Long userId, Long configurationId);

  /** Finds a subscription by its one-way token hash. */
  Optional<BookableItemCalendarSubscription> findByTokenHash(String tokenHash);

  /** Saves and flushes a subscription so uniqueness failures occur inside the manager call. */
  BookableItemCalendarSubscription saveAndFlush(BookableItemCalendarSubscription subscription);

  /** Removes one user's subscription and returns the number of rows removed. */
  int removeForUserAndConfiguration(Long userId, Long configurationId);

  /** Removes all subscriptions for one configuration and returns the number of rows removed. */
  int deleteByConfigurationId(Long configurationId);
}
