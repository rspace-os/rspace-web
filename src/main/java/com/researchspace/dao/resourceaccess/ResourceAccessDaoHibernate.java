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
  public AuthorizationFacts lockAuthorizationFacts(ResourceAccess access, User subject) {
    if (access == null || access.getId() == null || subject == null || subject.getId() == null) {
      throw new IllegalArgumentException("Persisted access and subject are required");
    }
    Session session = sessionFactory.getCurrentSession();
    // A cached aggregate is version-checked here; an uncached one is read current.
    session.find(ResourceAccess.class, access.getId(), LockModeType.PESSIMISTIC_WRITE);
    // Fetching through the aggregate initializes not-yet-loaded assignments from this locking read
    // rather than from the transaction's older consistent snapshot.
    session
        .createQuery(
            "select access from ResourceAccess access "
                + "left join fetch access.assignments where access.id = :accessId",
            ResourceAccess.class)
        .setParameter("accessId", access.getId())
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .getResultList();

    List<Boolean> enabledRows =
        session
            .createNativeQuery(
                "SELECT account_enabled FROM User WHERE id = :subjectId LOCK IN SHARE MODE",
                Boolean.class)
            .setParameter("subjectId", subject.getId())
            .getResultList();
    if (enabledRows.isEmpty()) {
      throw new IllegalArgumentException("Subject no longer exists");
    }
    boolean enabled = enabledRows.get(0);
    User lockedSubject = session.find(User.class, subject.getId());

    List<Long> currentGroupIds =
        session
            .createQuery(
                "select membership.group.id from UserGroup membership "
                    + "where membership.user.id = :subjectId",
                Long.class)
            .setParameter("subjectId", subject.getId())
            .setLockMode(LockModeType.PESSIMISTIC_READ)
            .getResultList();
    if (!currentGroupIds.isEmpty()) {
      session
          .createQuery("select group from Group group where group.id in :ids", Group.class)
          .setParameter("ids", currentGroupIds)
          .setLockMode(LockModeType.PESSIMISTIC_READ)
          .getResultList();
    }
    List<Long> currentRoleIds =
        session
            .createNativeQuery(
                "SELECT role_id FROM user_role WHERE user_id = :subjectId LOCK IN SHARE MODE",
                Long.class)
            .setParameter("subjectId", subject.getId())
            .getResultList();
    Set<String> globalRoleNames =
        currentRoleIds.isEmpty()
            ? Set.of()
            : Set.copyOf(
                session
                    .createQuery(
                        "select role.name from Role role where role.id in :roleIds", String.class)
                    .setParameter("roleIds", currentRoleIds)
                    .getResultList());
    return new AuthorizationFacts(
        lockedSubject, enabled, Set.copyOf(currentGroupIds), globalRoleNames);
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
      List<Boolean> enabledRows =
          session
              .createNativeQuery(
                  "SELECT account_enabled FROM User WHERE id = :userId LOCK IN SHARE MODE",
                  Boolean.class)
              .setParameter("userId", userId)
              .getResultList();
      User user =
          enabledRows.size() == 1 && enabledRows.get(0) ? session.find(User.class, userId) : null;
      return user != null ? ResourceRoleAssignment.forUser(roleKey, user) : null;
    }
    Long groupId = ResourceGranteeKeys.parseGroupId(granteeKey);
    if (groupId != null) {
      List<Long> groupIds =
          session
              .createNativeQuery(
                  "SELECT accessGroup.id FROM rsGroup accessGroup "
                      + "JOIN UserGroup membership ON membership.group_id = accessGroup.id "
                      + "JOIN User enabledMember ON enabledMember.id = membership.user_id "
                      + "WHERE accessGroup.id = :groupId "
                      + "AND enabledMember.account_enabled = true LOCK IN SHARE MODE",
                  Long.class)
              .setParameter("groupId", groupId)
              .getResultList();
      Group group = groupIds.isEmpty() ? null : session.find(Group.class, groupIds.get(0));
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
    Session session = sessionFactory.getCurrentSession();
    // Override legacy eager principal collections: the document only needs the grantee entities.
    var graph = session.createEntityGraph(ResourceAccess.class);
    var assignments = graph.addSubgraph("assignments", ResourceRoleAssignment.class);
    assignments.addSubgraph("user", User.class);
    assignments.addSubgraph("group", Group.class);
    session
        .createQuery(
            "select distinct access from ResourceAccess access "
                + "left join fetch access.assignments assignment "
                + "left join fetch assignment.user left join fetch assignment.group "
                + "where access.id in :accessIds",
            ResourceAccess.class)
        .setHint("jakarta.persistence.fetchgraph", graph)
        .setParameter("accessIds", accessIds)
        .getResultList();
  }

  @Override
  public Set<Long> availableGroupIds(Collection<Long> groupIds) {
    if (groupIds.isEmpty()) return Set.of();
    return Set.copyOf(
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select distinct membership.group.id from UserGroup membership "
                    + "where membership.group.id in :groupIds and membership.user.enabled = true",
                Long.class)
            .setParameter("groupIds", groupIds)
            .getResultList());
  }

  @Override
  public void flush() {
    sessionFactory.getCurrentSession().flush();
  }
}
