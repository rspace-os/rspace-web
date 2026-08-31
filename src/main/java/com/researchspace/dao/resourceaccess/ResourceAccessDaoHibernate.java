package com.researchspace.dao.resourceaccess;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceAudience;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Repository;

/** Hibernate persistence for the generic resource-access aggregate. */
@Repository
public class ResourceAccessDaoHibernate implements ResourceAccessDao {

  private static final String USER_PREFIX = "user:";
  private static final String GROUP_PREFIX = "group:";
  private static final String ALL_USERS_KEY = "audience:all-users";

  private final SessionFactory sessionFactory;

  public ResourceAccessDaoHibernate(SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public User lockAuthorizationFacts(ResourceAccess access, User subject) {
    if (access == null || access.getId() == null || subject == null || subject.getId() == null) {
      throw new IllegalArgumentException("Persisted access and subject are required");
    }
    Session session = sessionFactory.getCurrentSession();
    session.find(ResourceAccess.class, access.getId(), LockModeType.PESSIMISTIC_WRITE);
    session
        .createQuery(
            "select assignment from ResourceRoleAssignment assignment "
                + "where assignment.resourceAccess.id = :accessId",
            ResourceRoleAssignment.class)
        .setParameter("accessId", access.getId())
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .getResultList();

    User lockedSubject = session.find(User.class, subject.getId(), LockModeType.PESSIMISTIC_WRITE);
    if (lockedSubject == null) {
      throw new IllegalArgumentException("Subject no longer exists");
    }

    List<Long> currentGroupIds =
        session
            .createQuery(
                "select membership.group.id from UserGroup membership "
                    + "where membership.user.id = :subjectId",
                Long.class)
            .setParameter("subjectId", subject.getId())
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultList();
    if (!currentGroupIds.isEmpty()) {
      session
          .createQuery("select group from Group group where group.id in :ids", Group.class)
          .setParameter("ids", currentGroupIds)
          .setLockMode(LockModeType.PESSIMISTIC_WRITE)
          .getResultList();
    }
    session
        .createNativeQuery("SELECT role_id FROM user_role WHERE user_id = :subjectId FOR UPDATE")
        .setParameter("subjectId", subject.getId())
        .getResultList();
    lockedSubject.getRoles().size();
    lockedSubject.getUserGroups().size();
    return lockedSubject;
  }

  @Override
  public ResourceRoleAssignment resolveAvailable(String granteeKey, String roleKey) {
    if (granteeKey == null || roleKey == null) {
      return null;
    }
    Session session = sessionFactory.getCurrentSession();
    if (granteeKey.startsWith(USER_PREFIX)) {
      Long id = parseNonZeroId(granteeKey, USER_PREFIX);
      User user = id == null ? null : session.find(User.class, id);
      return user != null && user.isEnabled()
          ? ResourceRoleAssignment.forUser(roleKey, user)
          : null;
    }
    if (granteeKey.startsWith(GROUP_PREFIX)) {
      Long id = parseNonZeroId(granteeKey, GROUP_PREFIX);
      if (id == null) {
        return null;
      }
      Long enabledMembers =
          session
              .createQuery(
                  "select count(membership) from UserGroup membership "
                      + "where membership.group.id = :groupId and membership.user.enabled = true",
                  Long.class)
              .setParameter("groupId", id)
              .getSingleResult();
      Group group = enabledMembers > 0 ? session.find(Group.class, id) : null;
      return group == null ? null : ResourceRoleAssignment.forGroup(roleKey, group);
    }
    return ALL_USERS_KEY.equals(granteeKey)
        ? ResourceRoleAssignment.forAudience(roleKey, ResourceAudience.ALL_USERS)
        : null;
  }

  @Override
  public Map<Long, Set<Long>> assignedUserGroupIds(ResourceAccess access) {
    Set<Long> userIds = new LinkedHashSet<>();
    Set<Long> groupIds = new LinkedHashSet<>();
    access
        .getAssignments()
        .forEach(
            assignment -> {
              if (assignment.getUser() != null) {
                userIds.add(assignment.getUser().getId());
              }
              if (assignment.getGroup() != null) {
                groupIds.add(assignment.getGroup().getId());
              }
            });
    if (userIds.isEmpty() || groupIds.isEmpty()) {
      return Map.of();
    }
    List<Object[]> rows =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select membership.user.id, membership.group.id from UserGroup membership "
                    + "where membership.user.id in :userIds and membership.group.id in :groupIds",
                Object[].class)
            .setParameter("userIds", userIds)
            .setParameter("groupIds", groupIds)
            .getResultList();
    Map<Long, Set<Long>> result = new LinkedHashMap<>();
    rows.forEach(
        row ->
            result
                .computeIfAbsent((Long) row[0], ignored -> new LinkedHashSet<>())
                .add((Long) row[1]));
    result.replaceAll((ignored, ids) -> Set.copyOf(ids));
    return Map.copyOf(result);
  }

  @Override
  public void loadAssignments(Collection<Long> accessIds) {
    if (accessIds.isEmpty()) {
      return;
    }
    sessionFactory
        .getCurrentSession()
        .createQuery(
            "select distinct access from ResourceAccess access "
                + "left join fetch access.assignments where access.id in :accessIds",
            ResourceAccess.class)
        .setParameter("accessIds", accessIds)
        .getResultList();
  }

  @Override
  public void flush() {
    sessionFactory.getCurrentSession().flush();
  }

  private static Long parseNonZeroId(String key, String prefix) {
    try {
      long id = Long.parseLong(key.substring(prefix.length()));
      return id != 0 ? id : null;
    } catch (NumberFormatException ignored) {
      return null;
    }
  }
}
