package com.researchspace.dao.resourceaccess;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceGranteeKeys;
import com.researchspace.model.resourceaccess.ResourceGranteeKind;
import com.researchspace.service.resourceaccess.ResourceGranteeDirectoryEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
  public List<ResourceGranteeDirectoryEntry> search(String query, int limit, User subject) {
    String pattern = "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%";
    Session session = sessionFactory.getCurrentSession();
    boolean sysadmin = subject.hasSysadminRole();
    String userScope =
        sysadmin
            ? ""
            : " and exists (select candidateMembership.id from UserGroup candidateMembership "
                + "where candidateMembership.user.id = user.id and candidateMembership.group.id "
                + "in (select ownMembership.group.id from UserGroup ownMembership "
                + "where ownMembership.user.id = :subjectId))";
    var users =
        session
            .createQuery(
                "select user from User user where user.enabled = true "
                    + "and (lower(user.username) like :query escape '\\' "
                    + "or lower(user.firstName) like :query escape '\\' "
                    + "or lower(user.lastName) like :query escape '\\')"
                    + userScope
                    + " order by lower(user.lastName), lower(user.firstName), lower(user.username)",
                User.class)
            .setParameter("query", pattern)
            .setMaxResults(limit);
    if (!sysadmin) {
      users.setParameter("subjectId", subject.getId());
    }

    String groupScope =
        sysadmin
            ? ""
            : " and exists (select membership.id from UserGroup membership "
                + "where membership.group.id = accessGroup.id "
                + "and membership.user.id = :subjectId)";
    var groups =
        session
            .createQuery(
                "select accessGroup from Group accessGroup "
                    + "where exists (select enabledMembership.id from UserGroup enabledMembership "
                    + "where enabledMembership.group.id = accessGroup.id "
                    + "and enabledMembership.user.enabled = true) "
                    + "and (lower(accessGroup.displayName) like :query escape '\\' "
                    + "or lower(accessGroup.uniqueName) like :query escape '\\')"
                    + groupScope
                    + " order by lower(accessGroup.displayName), accessGroup.id",
                Group.class)
            .setParameter("query", pattern)
            .setMaxResults(limit);
    if (!sysadmin) {
      groups.setParameter("subjectId", subject.getId());
    }

    List<ResourceGranteeDirectoryEntry> result = new ArrayList<>();
    users.getResultList().stream()
        .map(ResourceAccessDirectoryDaoHibernate::user)
        .forEach(result::add);
    groups.getResultList().stream()
        .map(ResourceAccessDirectoryDaoHibernate::group)
        .forEach(result::add);
    return result.stream()
        .sorted(
            Comparator.comparing(
                ResourceGranteeDirectoryEntry::name, String.CASE_INSENSITIVE_ORDER))
        .limit(limit)
        .toList();
  }

  private static ResourceGranteeDirectoryEntry user(User user) {
    return new ResourceGranteeDirectoryEntry(
        ResourceGranteeKind.USER,
        user.getId(),
        ResourceGranteeKeys.user(user.getId()),
        user.getDisplayName(),
        user.getUsername());
  }

  private static ResourceGranteeDirectoryEntry group(Group group) {
    return new ResourceGranteeDirectoryEntry(
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
