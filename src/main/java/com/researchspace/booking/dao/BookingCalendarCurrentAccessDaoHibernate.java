package com.researchspace.booking.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Reads authorization facts as locking scalar rows.
 *
 * <p>The singleton permission gate prevents participating writers from adding an unobserved
 * membership while these absence-sensitive reads run. No managed User, group, role, or Instrument
 * state is consulted.
 */
@Repository
public class BookingCalendarCurrentAccessDaoHibernate implements BookingCalendarCurrentAccessDao {

  private static final Logger LOG =
      LoggerFactory.getLogger(BookingCalendarCurrentAccessDaoHibernate.class);
  private static final int PAGE_SIZE = 200;

  private final SessionFactory sessionFactory;
  private final BookingCalendarPermissionGateDao permissionGate;
  private final BookingCalendarSubscriptionDao subscriptions;

  public BookingCalendarCurrentAccessDaoHibernate(
      SessionFactory sessionFactory,
      BookingCalendarPermissionGateDao permissionGate,
      BookingCalendarSubscriptionDao subscriptions) {
    this.sessionFactory = sessionFactory;
    this.permissionGate = permissionGate;
    this.subscriptions = subscriptions;
  }

  @Override
  public Access resolve(Long subjectId, Long instrumentId) {
    permissionGate.requireHeld(BookingCalendarPermissionGateDao.Mode.SHARED);
    try {
      session().flush();
      Target target = target(instrumentId);
      if (target == null || target.deleted || !"Instrument".equals(target.discriminator)) {
        return Access.DENIED;
      }
      Map<Long, Person> people = people(subjectId, target.ownerId);
      Person subject = people.get(subjectId);
      Person owner = people.get(target.ownerId);
      if (subject == null || owner == null || !subject.enabled || subject.locked) {
        return Access.DENIED;
      }
      Set<String> roles = roles(subjectId);
      if (subjectId.equals(target.ownerId) || roles.contains("ROLE_SYSADMIN")) {
        return Access.OWNER;
      }
      List<Membership> memberships = memberships(subjectId, target.ownerId);
      Set<Long> subjectGroups = groupsFor(memberships, subjectId);
      Set<Long> ownerGroups = groupsFor(memberships, target.ownerId);
      boolean sameGroup = subjectGroups.stream().anyMatch(ownerGroups::contains);
      boolean whitelisted =
          target.sharingMode == 1
              && memberships.stream()
                  .filter(membership -> membership.userId.equals(subjectId))
                  .map(Membership::groupName)
                  .anyMatch(group -> aclContains(target.acl, group));
      boolean direct = (target.sharingMode == 0 && sameGroup) || whitelisted;
      if (direct) {
        return Access.BOOKER;
      }
      boolean roleVisible =
          roles.contains("ROLE_ADMIN")
              ? visibleInSoleAdminCommunity(subjectId, target.ownerId, ownerGroups)
              : visibleToPiOrLabAdmin(subjectId, ownerGroups, memberships);
      return roleVisible ? Access.VIEWER : Access.DENIED;
    } catch (RuntimeException failure) {
      permissionGate.markRollbackOnly();
      LOG.warn("Current booking calendar authorization lookup failed", failure);
      throw failure;
    }
  }

  @Override
  public int revokeDenied(Set<Long> userIds, Set<Long> instrumentIds, boolean all) {
    permissionGate.requireHeld(BookingCalendarPermissionGateDao.Mode.EXCLUSIVE);
    if (!all && userIds.isEmpty() && instrumentIds.isEmpty()) {
      return 0;
    }
    try {
      session().flush();
      int removed = 0;
      long afterId = Long.MIN_VALUE;
      List<Candidate> candidates = new ArrayList<>();
      while (true) {
        List<Candidate> page = candidates(afterId, userIds, instrumentIds, all);
        if (page.isEmpty()) {
          break;
        }
        candidates.addAll(page);
        afterId = page.get(page.size() - 1).subscriptionId;
        if (page.size() < PAGE_SIZE) {
          break;
        }
      }
      candidates.stream()
          .filter(candidate -> candidate.instrumentId != null)
          .map(Candidate::instrumentId)
          .distinct()
          .sorted()
          .forEach(this::target);
      subscriptions.lockPermissionFence();
      for (Candidate candidate : candidates) {
        if (candidate.instrumentId == null
            || !"INSTRUMENT".equals(candidate.targetType)
            || resolve(candidate.userId, candidate.instrumentId) == Access.DENIED) {
          removed += subscriptions.deleteById(candidate.subscriptionId);
        }
      }
      session().flush();
      return removed;
    } catch (RuntimeException failure) {
      permissionGate.markRollbackOnly();
      LOG.warn("Booking calendar permission-loss revocation failed", failure);
      throw failure;
    }
  }

  private Target target(Long instrumentId) {
    List<Object[]> rows =
        session()
            .createNativeQuery(
                "select owner_id, sharingMode, acl, deleted, DTYPE from InstrumentEntity"
                    + " where id = :id for update",
                Object[].class)
            .setParameter("id", instrumentId)
            .getResultList();
    if (rows.isEmpty()) {
      return null;
    }
    Object[] row = rows.get(0);
    return new Target(
        number(row[0]),
        ((Number) row[1]).intValue(),
        (String) row[2],
        bit(row[3]),
        (String) row[4]);
  }

  private Map<Long, Person> people(Long subjectId, Long ownerId) {
    List<Long> ids =
        subjectId.equals(ownerId)
            ? List.of(subjectId)
            : List.of(subjectId, ownerId).stream().sorted().toList();
    Map<Long, Person> result = new HashMap<>();
    session()
        .createNativeQuery(
            "select id, account_enabled, account_locked from User where id in (:ids)"
                + " order by id for update",
            Object[].class)
        .setParameterList("ids", ids)
        .getResultList()
        .forEach(
            row ->
                result.put(number(row[0]), new Person(number(row[0]), bit(row[1]), bit(row[2]))));
    return result;
  }

  private Set<String> roles(Long userId) {
    return new HashSet<>(
        session()
            .createNativeQuery(
                "select roles.name from user_role membership join roles"
                    + " on roles.id = membership.role_id where membership.user_id = :userId"
                    + " order by roles.id for update",
                String.class)
            .setParameter("userId", userId)
            .getResultList());
  }

  private List<Membership> memberships(Long subjectId, Long ownerId) {
    List<Long> ids =
        subjectId.equals(ownerId)
            ? List.of(subjectId)
            : List.of(subjectId, ownerId).stream().sorted().toList();
    return session()
        .createNativeQuery(
            "select membership.user_id, membership.group_id, membership.roleInGroup,"
                + " membership.adminViewDocsEnabled, groups.uniqueName from UserGroup membership"
                + " join rsGroup groups on groups.id = membership.group_id where membership.user_id"
                + " in (:ids) order by membership.id for update",
            Object[].class)
        .setParameterList("ids", ids)
        .getResultStream()
        .map(
            row ->
                new Membership(
                    number(row[0]),
                    number(row[1]),
                    ((Number) row[2]).intValue(),
                    bit(row[3]),
                    (String) row[4]))
        .toList();
  }

  private boolean visibleInSoleAdminCommunity(Long subjectId, Long ownerId, Set<Long> ownerGroups) {
    List<Long> communities =
        session()
            .createNativeQuery(
                "select community_id from community_admin where user_id = :userId"
                    + " order by community_id for update",
                Long.class)
            .setParameter("userId", subjectId)
            .getResultList();
    if (communities.size() > 1) {
      throw new IllegalStateException("A non-system administrator belongs to multiple communities");
    }
    if (communities.isEmpty()) {
      return false;
    }
    if (subjectId.equals(ownerId)) {
      return true;
    }
    List<Long> communityGroups =
        session()
            .createNativeQuery(
                "select group_id from community_labGroups where community_id = :communityId"
                    + " order by group_id for update",
                Long.class)
            .setParameter("communityId", communities.get(0))
            .getResultList();
    return communityGroups.stream().anyMatch(ownerGroups::contains);
  }

  private static boolean visibleToPiOrLabAdmin(
      Long subjectId, Set<Long> ownerGroups, List<Membership> memberships) {
    return memberships.stream()
        .filter(membership -> membership.userId.equals(subjectId))
        .filter(
            membership ->
                membership.role == 1 || (membership.role == 2 && membership.adminViewDocs))
        .map(Membership::groupId)
        .anyMatch(ownerGroups::contains);
  }

  private List<Candidate> candidates(
      long afterId, Set<Long> userIds, Set<Long> instrumentIds, boolean all) {
    StringBuilder sql =
        new StringBuilder(
            "select subscription.id, subscription.user_id, configuration.targetType,"
                + " configuration.targetId from BookableItemCalendarSubscription subscription"
                + " left join BookingConfiguration configuration"
                + " on configuration.id = subscription.bookingConfiguration_id"
                + " where subscription.id > :afterId");
    if (!all) {
      List<String> restrictions = new ArrayList<>();
      if (!userIds.isEmpty()) {
        restrictions.add("subscription.user_id in (:userIds)");
      }
      if (!instrumentIds.isEmpty()) {
        restrictions.add("configuration.targetId in (:instrumentIds)");
      }
      sql.append(" and (").append(String.join(" or ", restrictions)).append(")");
    }
    sql.append(" order by subscription.id limit ").append(PAGE_SIZE).append(" for update");
    var query =
        session()
            .createNativeQuery(sql.toString(), Object[].class)
            .setParameter("afterId", afterId);
    if (!all && !userIds.isEmpty()) {
      query.setParameterList("userIds", userIds);
    }
    if (!all && !instrumentIds.isEmpty()) {
      query.setParameterList("instrumentIds", instrumentIds);
    }
    return query
        .getResultStream()
        .map(
            row ->
                new Candidate(
                    number(row[0]),
                    number(row[1]),
                    (String) row[2],
                    row[3] == null ? null : number(row[3])))
        .toList();
  }

  private Session session() {
    return sessionFactory.getCurrentSession();
  }

  private static boolean aclContains(String acl, String group) {
    if (acl == null || group == null) {
      return false;
    }
    for (String element : acl.split("&")) {
      int equals = element.indexOf('=');
      if (equals >= 0 && element.substring(0, equals).equals(group)) {
        return true;
      }
    }
    return false;
  }

  private static Set<Long> groupsFor(List<Membership> memberships, Long userId) {
    Set<Long> result = new HashSet<>();
    memberships.stream()
        .filter(membership -> membership.userId.equals(userId))
        .map(Membership::groupId)
        .forEach(result::add);
    return result;
  }

  private static Long number(Object value) {
    return ((Number) value).longValue();
  }

  private static boolean bit(Object value) {
    return value instanceof Boolean booleanValue ? booleanValue : ((Number) value).intValue() != 0;
  }

  private record Target(
      Long ownerId, int sharingMode, String acl, boolean deleted, String discriminator) {}

  private record Person(Long id, boolean enabled, boolean locked) {}

  private record Membership(
      Long userId, Long groupId, int role, boolean adminViewDocs, String groupName) {}

  private record Candidate(
      Long subscriptionId, Long userId, String targetType, Long instrumentId) {}
}
