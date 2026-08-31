package com.researchspace.booking.dao;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.booking.UserBookingCalendarSubscription;
import java.util.Optional;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Repository;

/** Hibernate persistence for user-wide booking calendar subscriptions. */
@Repository("userBookingCalendarSubscriptionDao")
public class UserBookingCalendarSubscriptionDaoHibernate
    extends GenericDaoHibernate<UserBookingCalendarSubscription, Long>
    implements UserBookingCalendarSubscriptionDao {

  public UserBookingCalendarSubscriptionDaoHibernate(SessionFactory sessionFactory) {
    super(UserBookingCalendarSubscription.class, sessionFactory);
  }

  @Override
  public Optional<UserBookingCalendarSubscription> findByUserId(Long userId) {
    return getSession()
        .createQuery(
            "from UserBookingCalendarSubscription where user.id = :userId",
            UserBookingCalendarSubscription.class)
        .setParameter("userId", userId)
        .uniqueResultOptional();
  }

  @Override
  public Optional<UserBookingCalendarSubscription> findByTokenHash(String tokenHash) {
    return getSession()
        .createQuery(
            "from UserBookingCalendarSubscription subscription"
                + " join fetch subscription.user where subscription.tokenHash = :tokenHash",
            UserBookingCalendarSubscription.class)
        .setParameter("tokenHash", tokenHash)
        .uniqueResultOptional();
  }

  @Override
  public UserBookingCalendarSubscription saveAndFlush(
      UserBookingCalendarSubscription subscription) {
    UserBookingCalendarSubscription saved = save(subscription);
    getSession().flush();
    return saved;
  }

  @Override
  public int removeForUser(Long userId) {
    return getSession()
        .createMutationQuery("delete from UserBookingCalendarSubscription where user.id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
  }
}
