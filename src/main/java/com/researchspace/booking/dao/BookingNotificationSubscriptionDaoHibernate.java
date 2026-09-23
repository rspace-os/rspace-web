package com.researchspace.booking.dao;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.booking.BookableItemNotificationSubscription;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Repository;

/** Hibernate persistence for personal instrument booking-notification subscriptions. */
@Repository("bookingNotificationSubscriptionDao")
public class BookingNotificationSubscriptionDaoHibernate
    extends GenericDaoHibernate<BookableItemNotificationSubscription, Long>
    implements BookingNotificationSubscriptionDao {

  public BookingNotificationSubscriptionDaoHibernate(SessionFactory sessionFactory) {
    super(BookableItemNotificationSubscription.class, sessionFactory);
  }

  @Override
  public Optional<BookableItemNotificationSubscription> findByUserAndInstrument(
      Long userId, Long instrumentId) {
    return getSession()
        .createQuery(
            "from BookableItemNotificationSubscription where user.id = :userId"
                + " and instrument.id = :instrumentId",
            BookableItemNotificationSubscription.class)
        .setParameter("userId", userId)
        .setParameter("instrumentId", instrumentId)
        .uniqueResultOptional();
  }

  @Override
  public List<BookableItemNotificationSubscription> findEnabledByInstrument(Long instrumentId) {
    return getSession()
        .createQuery(
            "select distinct subscription from BookableItemNotificationSubscription subscription"
                + " join fetch subscription.user recipient"
                + " left join fetch recipient.userPreferences"
                + " where subscription.instrument.id = :instrumentId"
                + " and subscription.enabled = true",
            BookableItemNotificationSubscription.class)
        .setParameter("instrumentId", instrumentId)
        .setCacheable(false)
        .setReadOnly(true)
        .list();
  }

  @Override
  public Optional<BookableItemNotificationSubscription> findByUserAndInstrumentForUpdate(
      Long userId, Long instrumentId) {
    return getSession()
        .createQuery(
            "from BookableItemNotificationSubscription where user.id = :userId"
                + " and instrument.id = :instrumentId",
            BookableItemNotificationSubscription.class)
        .setParameter("userId", userId)
        .setParameter("instrumentId", instrumentId)
        .setLockOptions(new LockOptions(LockMode.PESSIMISTIC_WRITE))
        .uniqueResultOptional()
        .map(
            subscription -> {
              getSession().refresh(subscription, LockMode.PESSIMISTIC_WRITE);
              return subscription;
            });
  }

  @Override
  public List<BookableItemNotificationSubscription> findByUserAndInstruments(
      Long userId, List<Long> instrumentIds) {
    if (instrumentIds.isEmpty()) return List.of();
    return getSession()
        .createQuery(
            "from BookableItemNotificationSubscription where user.id = :userId"
                + " and instrument.id in :instrumentIds",
            BookableItemNotificationSubscription.class)
        .setParameter("userId", userId)
        .setParameterList("instrumentIds", instrumentIds)
        .list();
  }

  @Override
  public List<BookableItemNotificationSubscription> findByUserAndInstrumentsForUpdate(
      Long userId, List<Long> instrumentIds) {
    if (instrumentIds.isEmpty()) return List.of();
    List<BookableItemNotificationSubscription> result =
        getSession()
            .createQuery(
                "from BookableItemNotificationSubscription where user.id = :userId"
                    + " and instrument.id in :instrumentIds order by instrument.id",
                BookableItemNotificationSubscription.class)
            .setParameter("userId", userId)
            .setParameterList("instrumentIds", instrumentIds)
            .setLockOptions(new LockOptions(LockMode.PESSIMISTIC_WRITE))
            .list();
    result.forEach(subscription -> getSession().refresh(subscription, LockMode.PESSIMISTIC_WRITE));
    return result;
  }

  @Override
  public List<Long> findInstrumentIdsByUser(Long userId) {
    return getSession()
        .createQuery(
            "select instrument.id from BookableItemNotificationSubscription"
                + " where user.id = :userId order by instrument.id",
            Long.class)
        .setParameter("userId", userId)
        .list();
  }

  @Override
  public int unsubscribeEnabledForUser(Long userId, Date updatedAt) {
    return getSession()
        .createMutationQuery(
            "update BookableItemNotificationSubscription set enabled = false,"
                + " version = version + 1, updatedAt = :updatedAt"
                + " where user.id = :userId and enabled = true")
        .setParameter("userId", userId)
        .setParameter("updatedAt", updatedAt)
        .executeUpdate();
  }

  @Override
  public BookableItemNotificationSubscription saveAndFlush(
      BookableItemNotificationSubscription subscription) {
    BookableItemNotificationSubscription saved = save(subscription);
    getSession().flush();
    return saved;
  }
}
