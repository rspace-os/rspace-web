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
  public void lockPermissionFence() {
    // Permission changes are rare. A single fence keeps the creation/revocation protocol bounded;
    // replace with affected-user fences if measured contention warrants the broader mutation audit.
    getSession()
        .createNativeQuery(
            "select id from BookingConfigurationDefaults where id = 1 for update", Long.class)
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
  public List<BookingCalendarSubscriptionCandidate> findCandidatesByUserId(Long userId) {
    return candidates("where subscription.user.id = :userId", "userId", userId);
  }

  @Override
  public List<BookingCalendarSubscriptionCandidate> findCandidatesByConfigurationId(
      Long configurationId) {
    return candidates(
        "where subscription.bookingConfiguration.id = :configurationId",
        "configurationId",
        configurationId);
  }

  @Override
  public List<BookingCalendarSubscriptionCandidate> findCandidatesForMembershipRevalidation() {
    return candidates("", null, null);
  }

  private List<BookingCalendarSubscriptionCandidate> candidates(
      String restriction, String parameterName, Object parameterValue) {
    var query =
        getSession()
            .createQuery(
                "select new"
                    + " com.researchspace.booking.dao.BookingCalendarSubscriptionCandidate(subscription.id,"
                    + " subscription.user.id, subscription.bookingConfiguration.id) from"
                    + " BookableItemCalendarSubscription subscription "
                    + restriction,
                BookingCalendarSubscriptionCandidate.class);
    if (parameterName != null) {
      query.setParameter(parameterName, parameterValue);
    }
    return query.list();
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
