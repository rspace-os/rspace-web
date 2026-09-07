package com.researchspace.booking.dao;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.booking.BookableItemCalendarSubscription;
import java.util.List;
import java.util.Optional;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Repository;

/** Hibernate persistence for bookable-item calendar subscriptions. */
@Repository("bookingCalendarSubscriptionDao")
public class BookingCalendarSubscriptionDaoHibernate
    extends GenericDaoHibernate<BookableItemCalendarSubscription, Long>
    implements BookingCalendarSubscriptionDao {

  public BookingCalendarSubscriptionDaoHibernate(SessionFactory sessionFactory) {
    super(BookableItemCalendarSubscription.class, sessionFactory);
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
  public List<BookableItemCalendarSubscription> findByUserId(Long userId) {
    return getSession()
        .createQuery(
            "from BookableItemCalendarSubscription subscription"
                + " join fetch subscription.bookingConfiguration configuration"
                + " join fetch configuration.resourceAccess"
                + " where subscription.user.id = :userId",
            BookableItemCalendarSubscription.class)
        .setParameter("userId", userId)
        .list();
  }

  @Override
  public List<BookableItemCalendarSubscription> findByConfigurationId(Long configurationId) {
    return getSession()
        .createQuery(
            "from BookableItemCalendarSubscription subscription"
                + " join fetch subscription.user"
                + " where subscription.bookingConfiguration.id = :configurationId",
            BookableItemCalendarSubscription.class)
        .setParameter("configurationId", configurationId)
        .list();
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
  public int deleteByConfigurationId(Long configurationId) {
    return getSession()
        .createMutationQuery(
            "delete from BookableItemCalendarSubscription"
                + " where bookingConfiguration.id = :configurationId")
        .setParameter("configurationId", configurationId)
        .executeUpdate();
  }
}
