package com.researchspace.dao.resourceaccess;

import com.researchspace.Constants;
import com.researchspace.dao.query.LikeEscaper;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceGranteeKeys;
import com.researchspace.model.resourceaccess.ResourceGranteeKind;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

/** Hibernate implementation that performs one bounded query per principal type. */
@Repository
public class ResourceAccessDirectoryDaoHibernate implements ResourceAccessDirectoryDao {

  private static final String USER_MEMBERSHIP_SCOPE =
      "exists (select candidateMembership.id from UserGroup candidateMembership "
          + "where candidateMembership.user.id = user.id and candidateMembership.group.id "
          + "in (select ownMembership.group.id from UserGroup ownMembership "
          + "where ownMembership.user.id = :subjectId))";
  private static final String GROUP_HAS_ENABLED_MEMBERS =
      "exists (select enabledMembership.id from UserGroup enabledMembership "
          + "where enabledMembership.group.id = accessGroup.id "
          + "and enabledMembership.user.enabled = true)";
  private static final String GROUP_MEMBERSHIP_SCOPE =
      "exists (select membership.id from UserGroup membership "
          + "where membership.group.id = accessGroup.id and membership.user.id = :subjectId)";
  private final SessionFactory sessionFactory;

  public ResourceAccessDirectoryDaoHibernate(SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public List<ResourceAccessDirectoryRow> search(String query, int limit, User subject) {
    String pattern = "%" + LikeEscaper.escape(query.toLowerCase(Locale.ROOT)) + "%";
    Session session = sessionFactory.getCurrentSession();
    boolean sysadmin = subject.hasSysadminRole();
    StringBuilder userQuery =
        new StringBuilder("select user from User user where user.enabled = true ")
            .append("and (lower(user.username) like :query escape '!' ")
            .append("or lower(user.firstName) like :query escape '!' ")
            .append("or lower(user.lastName) like :query escape '!' ")
            .append("or lower(concat(user.firstName, ' ', user.lastName)) like :query escape '!')");
    if (!sysadmin) {
      userQuery.append(" and ").append(USER_MEMBERSHIP_SCOPE);
    }
    userQuery.append(" order by lower(user.lastName), lower(user.firstName), lower(user.username)");
    var users =
        session
            .createQuery(userQuery.toString(), User.class)
            .setParameter("query", pattern)
            .setMaxResults(limit);
    if (!sysadmin) {
      users.setParameter("subjectId", subject.getId());
    }

    StringBuilder groupQuery =
        new StringBuilder("select accessGroup from Group accessGroup where ")
            .append(GROUP_HAS_ENABLED_MEMBERS)
            .append(" and (lower(accessGroup.displayName) like :query escape '!' ")
            .append("or lower(accessGroup.uniqueName) like :query escape '!')");
    if (!sysadmin) {
      groupQuery.append(" and ").append(GROUP_MEMBERSHIP_SCOPE);
    }
    groupQuery.append(" order by lower(accessGroup.displayName), accessGroup.id");
    var groups =
        session
            .createQuery(groupQuery.toString(), Group.class)
            .setParameter("query", pattern)
            .setMaxResults(limit);
    if (!sysadmin) {
      groups.setParameter("subjectId", subject.getId());
    }

    return Stream.concat(
            users.getResultList().stream().map(ResourceAccessDirectoryDaoHibernate::user),
            groups.getResultList().stream().map(ResourceAccessDirectoryDaoHibernate::group))
        .sorted(
            Comparator.comparing(ResourceAccessDirectoryRow::name, String.CASE_INSENSITIVE_ORDER))
        .limit(limit)
        .toList();
  }

  @Override
  public Map<String, ResourceAccessDirectoryRow> resolveAssignable(
      Set<String> keys,
      boolean subjectEnabled,
      Set<Long> currentGroupIds,
      Set<String> globalRoleNames) {
    if (keys.isEmpty() || !subjectEnabled) {
      return Map.of();
    }
    Set<Long> userIds = ids(keys, ResourceGranteeKeys::parseUserId);
    Set<Long> groupIds = ids(keys, ResourceGranteeKeys::parseGroupId);
    boolean sysadmin = globalRoleNames.contains(Constants.SYSADMIN_ROLE);
    if (!sysadmin && currentGroupIds.isEmpty()) {
      return Map.of();
    }
    Session session = sessionFactory.getCurrentSession();
    List<User> users =
        userIds.isEmpty()
            ? List.of()
            : assignableUsers(session, userIds, currentGroupIds, sysadmin);
    List<Group> groups =
        groupIds.isEmpty()
            ? List.of()
            : assignableGroups(session, groupIds, currentGroupIds, sysadmin);
    Map<String, ResourceAccessDirectoryRow> resolved = new LinkedHashMap<>();
    users.stream()
        .map(ResourceAccessDirectoryDaoHibernate::user)
        .forEach(entry -> resolved.put(entry.key(), entry));
    groups.stream()
        .map(ResourceAccessDirectoryDaoHibernate::group)
        .forEach(entry -> resolved.put(entry.key(), entry));
    return Map.copyOf(resolved);
  }

  private static List<User> assignableUsers(
      Session session, Set<Long> ids, Set<Long> currentGroupIds, boolean sysadmin) {
    StringBuilder sql = new StringBuilder("SELECT candidate.id FROM User candidate ");
    if (!sysadmin) {
      sql.append("JOIN UserGroup membership ON membership.user_id = candidate.id ");
    }
    sql.append("WHERE candidate.id IN (:ids) AND candidate.account_enabled = true ");
    if (!sysadmin) {
      sql.append("AND membership.group_id IN (:currentGroupIds) ");
    }
    sql.append("FOR UPDATE");
    NativeQuery<Long> query = session.createNativeQuery(sql.toString(), Long.class);
    query.setParameterList("ids", ids);
    if (!sysadmin) {
      query.setParameterList("currentGroupIds", currentGroupIds);
    }
    return new LinkedHashSet<>(query.getResultList())
        .stream().map(id -> session.find(User.class, id)).filter(Objects::nonNull).toList();
  }

  private static List<Group> assignableGroups(
      Session session, Set<Long> ids, Set<Long> currentGroupIds, boolean sysadmin) {
    StringBuilder sql =
        new StringBuilder(
            "SELECT candidate.id FROM rsGroup candidate "
                + "JOIN UserGroup membership ON membership.group_id = candidate.id "
                + "JOIN User enabledMember ON enabledMember.id = membership.user_id "
                + "WHERE candidate.id IN (:ids) AND enabledMember.account_enabled = true ");
    if (!sysadmin) {
      sql.append("AND candidate.id IN (:currentGroupIds) ");
    }
    sql.append("FOR UPDATE");
    NativeQuery<Long> query = session.createNativeQuery(sql.toString(), Long.class);
    query.setParameterList("ids", ids);
    if (!sysadmin) {
      query.setParameterList("currentGroupIds", currentGroupIds);
    }
    return new LinkedHashSet<>(query.getResultList())
        .stream().map(id -> session.find(Group.class, id)).filter(Objects::nonNull).toList();
  }

  private static Set<Long> ids(Set<String> keys, Function<String, Long> parseId) {
    return keys.stream()
        .map(parseId)
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static ResourceAccessDirectoryRow user(User user) {
    return new ResourceAccessDirectoryRow(
        ResourceGranteeKind.USER,
        user.getId(),
        ResourceGranteeKeys.user(user.getId()),
        user.getDisplayName(),
        user.getUsername());
  }

  private static ResourceAccessDirectoryRow group(Group group) {
    return new ResourceAccessDirectoryRow(
        ResourceGranteeKind.GROUP,
        group.getId(),
        ResourceGranteeKeys.group(group.getId()),
        group.getDisplayName(),
        group.getUniqueName());
  }
}
