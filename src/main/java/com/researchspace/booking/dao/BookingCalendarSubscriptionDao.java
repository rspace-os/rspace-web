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

  /** Finds all of one user's item subscriptions with their configurations loaded. */
  List<BookableItemCalendarSubscription> findByUserId(Long userId);

  /** Finds a subscription by its one-way token hash. */
  Optional<BookableItemCalendarSubscription> findByTokenHash(String tokenHash);

  /** Loads subscription identities to revalidate after a user's status or memberships change. */
  List<BookingCalendarSubscriptionCandidate> findCandidatesByUserId(Long userId);

  /** Loads subscription identities to revalidate after a configuration's access changes. */
  List<BookingCalendarSubscriptionCandidate> findCandidatesByConfigurationId(Long configurationId);

  /** Loads subscription identities after changes to shared group/community permission sources. */
  List<BookingCalendarSubscriptionCandidate> findCandidatesForMembershipRevalidation();

  /** Saves and flushes a subscription so uniqueness failures occur inside the manager call. */
  BookableItemCalendarSubscription saveAndFlush(BookableItemCalendarSubscription subscription);

  /** Removes one user's subscription and returns the number of rows removed. */
  int removeForUserAndConfiguration(Long userId, Long configurationId);

  /** Removes a subscription by identity without loading it into the current session. */
  int deleteById(Long subscriptionId);

  /** Removes all subscriptions for one configuration and returns the number of rows removed. */
  int deleteByConfigurationId(Long configurationId);
}
