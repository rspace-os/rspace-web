package com.researchspace.dao.resourceaccess;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceGranteeKeys;
import com.researchspace.model.resourceaccess.ResourceGranteeKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Repository;

/** Hibernate implementation that performs one bounded query per principal type. */
@Repository
public class ResourceAccessDirectoryDaoHibernate implements ResourceAccessDirectoryDao {

  private final SessionFactory sessionFactory;

  public ResourceAccessDirectoryDaoHibernate(SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public List<ResourceAccessDirectoryRow> search(String query, int limit, User subject) {
    String pattern = "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%";
    Session session = sessionFactory.getCurrentSession();
    boolean sysadmin = subject.hasSysadminRole();
    StringBuilder userQuery =
        new StringBuilder("select user from User user where user.enabled = true ")
            .append("and (lower(user.username) like :query escape '\\' ")
            .append("or lower(user.firstName) like :query escape '\\' ")
            .append("or lower(user.lastName) like :query escape '\\')");
    if (!sysadmin) {
      userQuery
          .append(" and exists (select candidateMembership.id from UserGroup candidateMembership ")
          .append("where candidateMembership.user.id = user.id and candidateMembership.group.id ")
          .append("in (select ownMembership.group.id from UserGroup ownMembership ")
          .append("where ownMembership.user.id = :subjectId))");
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
        new StringBuilder("select accessGroup from Group accessGroup ")
            .append("where exists (select enabledMembership.id from UserGroup enabledMembership ")
            .append("where enabledMembership.group.id = accessGroup.id ")
            .append("and enabledMembership.user.enabled = true) ")
            .append("and (lower(accessGroup.displayName) like :query escape '\\' ")
            .append("or lower(accessGroup.uniqueName) like :query escape '\\')");
    if (!sysadmin) {
      groupQuery
          .append(" and exists (select membership.id from UserGroup membership ")
          .append("where membership.group.id = accessGroup.id ")
          .append("and membership.user.id = :subjectId)");
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

    List<ResourceAccessDirectoryRow> result = new ArrayList<>();
    users.getResultList().stream()
        .map(ResourceAccessDirectoryDaoHibernate::user)
        .forEach(result::add);
    groups.getResultList().stream()
        .map(ResourceAccessDirectoryDaoHibernate::group)
        .forEach(result::add);
    return result.stream()
        .sorted(
            Comparator.comparing(ResourceAccessDirectoryRow::name, String.CASE_INSENSITIVE_ORDER))
        .limit(limit)
        .toList();
  }

  @Override
  public Map<String, ResourceAccessDirectoryRow> resolveAssignable(Set<String> keys, User subject) {
    if (keys.isEmpty() || subject == null || !subject.isEnabled()) {
      return Map.of();
    }
    Set<Long> userIds = ids(keys, "user:");
    Set<Long> groupIds = ids(keys, "group:");
    boolean sysadmin = subject.hasSysadminRole();
    Session session = sessionFactory.getCurrentSession();
    List<User> users =
        userIds.isEmpty()
            ? List.of()
            : assignableUsers(session, userIds, subject.getId(), sysadmin);
    List<Group> groups =
        groupIds.isEmpty()
            ? List.of()
            : assignableGroups(session, groupIds, subject.getId(), sysadmin);
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
      Session session, Set<Long> ids, Long subjectId, boolean sysadmin) {
    StringBuilder hql =
        new StringBuilder(
            "select user from User user where user.id in :ids and user.enabled = true");
    if (!sysadmin) {
      hql.append(" and exists (select candidateMembership.id from UserGroup candidateMembership ")
          .append("where candidateMembership.user.id = user.id and candidateMembership.group.id ")
          .append("in (select ownMembership.group.id from UserGroup ownMembership ")
          .append("where ownMembership.user.id = :subjectId))");
    }
    var query = session.createQuery(hql.toString(), User.class);
    query.setParameter("ids", ids);
    if (!sysadmin) {
      query.setParameter("subjectId", subjectId);
    }
    return query.getResultList();
  }

  private static List<Group> assignableGroups(
      Session session, Set<Long> ids, Long subjectId, boolean sysadmin) {
    StringBuilder hql =
        new StringBuilder("select accessGroup from Group accessGroup where accessGroup.id in :ids ")
            .append("and exists (select enabledMembership.id from UserGroup enabledMembership ")
            .append("where enabledMembership.group.id = accessGroup.id ")
            .append("and enabledMembership.user.enabled = true)");
    if (!sysadmin) {
      hql.append(" and exists (select membership.id from UserGroup membership ")
          .append("where membership.group.id = accessGroup.id ")
          .append("and membership.user.id = :subjectId)");
    }
    var query = session.createQuery(hql.toString(), Group.class);
    query.setParameter("ids", ids);
    if (!sysadmin) {
      query.setParameter("subjectId", subjectId);
    }
    return query.getResultList();
  }

  private static Set<Long> ids(Set<String> keys, String prefix) {
    Set<Long> ids = new LinkedHashSet<>();
    for (String key : keys) {
      if (key == null || !key.startsWith(prefix)) {
        continue;
      }
      try {
        long id = Long.parseLong(key.substring(prefix.length()));
        if (id != 0) {
          ids.add(id);
        }
      } catch (NumberFormatException ignored) {
        // Invalid keys remain unresolved and receive the same response as out-of-scope keys.
      }
    }
    return ids;
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

  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
