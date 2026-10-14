package com.researchspace.dao.resourceaccess;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceAudience;
import com.researchspace.model.resourceaccess.ResourceGranteeKeys;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/** Hibernate persistence for the generic resource-access aggregate. */
@Repository
public class ResourceAccessDaoHibernate implements ResourceAccessDao {

  private final SessionFactory sessionFactory;
  private final CriteriaBuilderFactory criteriaBuilderFactory;

  public record UserGroupRow(Long userId, Long groupId) {}

  @Autowired
  public ResourceAccessDaoHibernate(
      SessionFactory sessionFactory, CriteriaBuilderFactory criteriaBuilderFactory) {
    this.sessionFactory = sessionFactory;
    this.criteriaBuilderFactory = criteriaBuilderFactory;
  }

  /**
   * Uses locking projections for write-time enabled, group and role facts. The returned User may be
   * cached; resolve permissions from the separate facts rather than its loaded associations.
   */
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
  public ResourceRoleAssignment resolveAvailable(
      String granteeKey, String roleKey, String audienceNameSnapshot) {
    if (granteeKey == null || roleKey == null) {
      return null;
    }
    Session session = sessionFactory.getCurrentSession();
    Long userId = ResourceGranteeKeys.parseUserId(granteeKey);
    if (userId != null) {
      User user = session.find(User.class, userId);
      return user != null && user.isEnabled()
          ? ResourceRoleAssignment.forUser(roleKey, user)
          : null;
    }
    Long groupId = ResourceGranteeKeys.parseGroupId(granteeKey);
    if (groupId != null) {
      Long enabledMembers =
          session
              .createQuery(
                  "select count(membership) from UserGroup membership "
                      + "where membership.group.id = :groupId and membership.user.enabled = true",
                  Long.class)
              .setParameter("groupId", groupId)
              .getSingleResult();
      Group group = enabledMembers > 0 ? session.find(Group.class, groupId) : null;
      return group == null ? null : ResourceRoleAssignment.forGroup(roleKey, group);
    }
    if (!ResourceGranteeKeys.audience(ResourceAudience.ALL_USERS).equals(granteeKey)) {
      return null;
    }
    if (audienceNameSnapshot == null || audienceNameSnapshot.isBlank()) {
      throw new IllegalArgumentException("Audience display name is required");
    }
    return ResourceRoleAssignment.forAudience(
        roleKey, ResourceAudience.ALL_USERS, audienceNameSnapshot);
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
    CriteriaBuilder<UserGroupRow> query =
        criteriaBuilderFactory
            .create(sessionFactory.getCurrentSession(), UserGroupRow.class)
            .from(UserGroup.class, "membership")
            .selectNew(UserGroupRow.class)
            .with("membership.user.id")
            .with("membership.group.id")
            .end()
            .whereExpression("membership.user.id in :userIds")
            .whereExpression("membership.group.id in :groupIds")
            .setParameter("userIds", userIds)
            .setParameter("groupIds", groupIds);
    Map<Long, Set<Long>> result = new LinkedHashMap<>();
    query
        .getResultList()
        .forEach(
            row ->
                result
                    .computeIfAbsent(row.userId(), ignored -> new LinkedHashSet<>())
                    .add(row.groupId()));
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
}
