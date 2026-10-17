package com.researchspace.booking.dao;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.booking.BookableItemCalendarSubscription;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import java.util.Date;
import java.util.Optional;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository("bookingCalendarSubscriptionDao")
public class BookingCalendarSubscriptionDaoHibernate
    extends GenericDaoHibernate<BookableItemCalendarSubscription, Long>
    implements BookingCalendarSubscriptionDao {

  private static final Logger LOG =
      LoggerFactory.getLogger(BookingCalendarSubscriptionDaoHibernate.class);
  private final BookingCalendarPermissionGateDao permissionGate;

  public BookingCalendarSubscriptionDaoHibernate(
      SessionFactory sessionFactory, BookingCalendarPermissionGateDao permissionGate) {
    super(BookableItemCalendarSubscription.class, sessionFactory);
    this.permissionGate = permissionGate;
  }

  @Override
  public void lockPermissionFence() {
    permissionGate.requireHeld(BookingCalendarPermissionGateDao.Mode.SHARED);
    // Permission changes are rare. A single fence keeps the creation/revocation protocol bounded;
    // replace with affected-user fences if measured contention warrants the broader mutation audit.
    getSession()
        .createNativeQuery(
            "select id from BookingConfigurationDefaults where id = :defaultsId for update",
            Long.class)
        .setParameter("defaultsId", BookingConfigurationDefaults.SINGLETON_ID)
        .getSingleResult();
  }

  @Override
  public Optional<BookableItemCalendarSubscription> findByUserIdAndConfigurationId(
      Long userId, Long configurationId) {
    return getSession()
        .createQuery(
            "from BookableItemCalendarSubscription where user.id = :userId"
                + " and bookingConfiguration.id = :configurationId",
            BookableItemCalendarSubscription.class)
        .setParameter("userId", userId)
        .setParameter("configurationId", configurationId)
        .uniqueResultOptional();
  }

  @Override
  public Optional<CurrentItemCalendarSubscription> lockCurrent(Long userId, Long configurationId) {
    try {
      getSession().flush();
      Optional<CurrentItemCalendarSubscription> current =
          getSession()
              .createNativeQuery(
                  "select id, user_id, bookingConfiguration_id, tokenHash, rawToken, updatedAt"
                      + " from BookableItemCalendarSubscription where user_id = :userId"
                      + " and bookingConfiguration_id = :configurationId for update",
                  Object[].class)
              .setParameter("userId", userId)
              .setParameter("configurationId", configurationId)
              .getResultStream()
              .map(BookingCalendarSubscriptionDaoHibernate::current)
              .findFirst();
      current.ifPresent(this::refreshManaged);
      return current;
    } catch (RuntimeException failure) {
      permissionGate.markRollbackOnly();
      LOG.warn("Current calendar subscription lookup failed", failure);
      throw failure;
    }
  }

  @Override
  public CurrentItemCalendarSubscription rotateCurrent(
      CurrentItemCalendarSubscription current,
      String expectedTokenHash,
      String newTokenHash,
      String newRawToken,
      Date updatedAt) {
    try {
      BookableItemCalendarSubscription managed =
          getSession()
              .find(
                  BookableItemCalendarSubscription.class,
                  current.id(),
                  LockModeType.PESSIMISTIC_WRITE);
      if (managed == null) {
        throw new OptimisticLockException("Calendar subscription was concurrently removed");
      }
      getSession().refresh(managed, LockModeType.PESSIMISTIC_WRITE);
      int changed =
          getSession()
              .createNativeMutationQuery(
                  "update BookableItemCalendarSubscription set tokenHash = :newTokenHash,"
                      + " rawToken = :newRawToken, updatedAt = :updatedAt"
                      + " where id = :id and tokenHash = :expectedTokenHash")
              .setParameter("newTokenHash", newTokenHash)
              .setParameter("newRawToken", newRawToken)
              .setParameter("updatedAt", updatedAt)
              .setParameter("id", current.id())
              .setParameter("expectedTokenHash", expectedTokenHash)
              .executeUpdate();
      if (changed != 1) {
        throw new OptimisticLockException("Calendar subscription ETag changed");
      }
      getSession().flush();
      getSession().refresh(managed, LockModeType.PESSIMISTIC_WRITE);
      return lockCurrent(current.userId(), current.configurationId())
          .orElseThrow(
              () -> new OptimisticLockException("Calendar subscription was concurrently removed"));
    } catch (RuntimeException failure) {
      permissionGate.markRollbackOnly();
      LOG.warn("Conditional calendar subscription rotation failed", failure);
      throw failure;
    }
  }

  @Override
  public CurrentItemCalendarSubscription reconcileCreated(Long userId, Long configurationId) {
    try {
      return lockCurrent(userId, configurationId)
          .orElseThrow(
              () -> new OptimisticLockException("Created calendar subscription is missing"));
    } catch (RuntimeException failure) {
      permissionGate.markRollbackOnly();
      LOG.warn("Created calendar subscription reconciliation failed", failure);
      throw failure;
    }
  }

  private void refreshManaged(CurrentItemCalendarSubscription current) {
    BookableItemCalendarSubscription managed =
        getSession().find(BookableItemCalendarSubscription.class, current.id());
    if (managed != null) {
      getSession().refresh(managed, LockModeType.PESSIMISTIC_WRITE);
    }
  }

  private static CurrentItemCalendarSubscription current(Object[] row) {
    return new CurrentItemCalendarSubscription(
        ((Number) row[0]).longValue(),
        ((Number) row[1]).longValue(),
        ((Number) row[2]).longValue(),
        (String) row[3],
        (String) row[4],
        (Date) row[5]);
  }

  @Override
  public Optional<BookableItemCalendarSubscription> findByTokenHash(String tokenHash) {
    return getSession()
        .createQuery(
            "from BookableItemCalendarSubscription subscription"
                + " join fetch subscription.bookingConfiguration"
                + " join fetch subscription.user where subscription.tokenHash = :tokenHash",
            BookableItemCalendarSubscription.class)
        .setParameter("tokenHash", tokenHash)
        .uniqueResultOptional();
  }

  @Override
  public BookableItemCalendarSubscription saveAndFlush(
      BookableItemCalendarSubscription subscription) {
    BookableItemCalendarSubscription saved = save(subscription);
    getSession().flush();
    return saved;
  }

  @Override
  public int removeForUserAndConfiguration(Long userId, Long configurationId) {
    return getSession()
        .createMutationQuery(
            "delete from BookableItemCalendarSubscription where user.id = :userId"
                + " and bookingConfiguration.id = :configurationId")
        .setParameter("userId", userId)
        .setParameter("configurationId", configurationId)
        .executeUpdate();
  }

  @Override
  public int deleteById(Long subscriptionId) {
    return getSession()
        .createMutationQuery("delete from BookableItemCalendarSubscription where id = :id")
        .setParameter("id", subscriptionId)
        .executeUpdate();
  }

  @Override
  public int deleteByConfigurationId(Long configurationId) {
    return getSession()
        .createMutationQuery(
            "delete from BookableItemCalendarSubscription"
                + " where bookingConfiguration.id = :configurationId")
        .setParameter("configurationId", configurationId)
        .executeUpdate();
  }
}
