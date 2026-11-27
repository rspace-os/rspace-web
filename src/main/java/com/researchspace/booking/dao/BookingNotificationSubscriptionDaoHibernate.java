package com.researchspace.booking.dao;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableItemNotificationSubscription;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.CacheMode;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Repository;

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
  public List<Long> findEnabledUserIds(Long instrumentId) {
    return getSession()
        .createQuery(
            "select subscription.user.id from BookableItemNotificationSubscription subscription"
                + " where subscription.instrument.id = :instrumentId"
                + " and subscription.enabled = true",
            Long.class)
        .setParameter("instrumentId", instrumentId)
        .setCacheable(false)
        .setCacheMode(CacheMode.IGNORE)
        .setReadOnly(true)
        .list();
  }

  @Override
  public List<User> findRecipientUsers(Set<Long> userIds) {
    if (userIds.isEmpty()) return List.of();
    var graph = getSession().createEntityGraph(User.class);
    graph.addAttributeNodes("userPreferences");
    return getSession()
        .createQuery(
            "select distinct u from User u left join fetch u.userPreferences where u.id in :ids",
            User.class)
        .setParameterList("ids", userIds)
        .setHint("jakarta.persistence.fetchgraph", graph)
        .setCacheMode(CacheMode.IGNORE)
        .list();
  }

  @Override
  public Set<Long> findReadableUsers(Long instrumentId, Set<Long> userIds) {
    if (userIds.isEmpty()) return Set.of();
    String sql =
        """
        select u.id from User u join InstrumentEntity i on i.id = :instrumentId
        where u.id in (:ids) and i.DTYPE = 'Instrument' and i.deleted = false
        and (u.id = i.owner_id
          or exists (select 1 from user_role ur join roles r on r.id = ur.role_id
                     where ur.user_id = u.id and r.name = 'ROLE_SYSADMIN')
          or exists (select 1 from UserGroup ug join UserGroup og on og.group_id = ug.group_id
                     where ug.user_id = u.id and og.user_id = i.owner_id and i.sharingMode = 0)
          or exists (select 1 from UserGroup ug join rsGroup g on g.id = ug.group_id
                     where ug.user_id = u.id and i.sharingMode = 1
                     and locate(concat('&', g.uniqueName, '='), concat('&', i.acl)) > 0)
          or (not exists (select 1 from user_role ur join roles r on r.id = ur.role_id
                          where ur.user_id = u.id and r.name = 'ROLE_ADMIN')
              and exists (select 1 from UserGroup ug join UserGroup og on og.group_id = ug.group_id
                          where ug.user_id = u.id and og.user_id = i.owner_id
                          and (ug.roleInGroup = 1 or (ug.roleInGroup = 2 and ug.adminViewDocsEnabled = true))))
          or (exists (select 1 from user_role ur join roles r on r.id = ur.role_id
                      where ur.user_id = u.id and r.name = 'ROLE_ADMIN')
              and (select count(*) from community_admin ca where ca.user_id = u.id) = 1
              and exists (select 1 from community_admin ca
                          join community_labGroups cg on cg.community_id = ca.community_id
                          join UserGroup og on og.group_id = cg.group_id
                          where ca.user_id = u.id and og.user_id = i.owner_id)))
        """;
    return new HashSet<>(
        getSession()
            .createNativeQuery(sql, Long.class)
            .setParameter("instrumentId", instrumentId)
            .setParameterList("ids", userIds)
            .setCacheMode(CacheMode.IGNORE)
            .list());
  }

  @Override
  public void initialize(Long userId, Long instrumentId, boolean enabled, Date now) {
    getSession().flush();
    getSession()
        .createNativeMutationQuery(
            "insert into BookableItemNotificationSubscription"
                + " (user_id, instrument_id, enabled, version, createdAt, updatedAt)"
                + " values (:userId, :instrumentId, :enabled, 0, :now, :now)"
                + " on duplicate key update id = id")
        .setParameter("userId", userId)
        .setParameter("instrumentId", instrumentId)
        .setParameter("enabled", enabled)
        .setParameter("now", now)
        .executeUpdate();
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
