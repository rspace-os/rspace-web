package com.researchspace.service.resourceaccess;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.Constants;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceAudience;
import com.researchspace.model.resourceaccess.ResourceGranteeKeys;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ContextConfiguration(initializers = ResourceAccessManagerAuthorizationIT.SchemeInitializer.class)
class ResourceAccessManagerAuthorizationIT extends RealTransactionSpringTestBase {

  private final ResourceAccessManager accessManager;
  private final com.researchspace.dao.resourceaccess.ResourceAccessDao accessDao;

  private String subjectUsername;
  private String outgoingOwnerUsername;
  private String incomingOwnerUsername;
  private String groupCandidateUsername;
  private String candidateGroupMemberUsername;
  private Long groupId;
  private Long candidateGroupId;
  private Long resourceAccessId;

  @Autowired
  ResourceAccessManagerAuthorizationIT(
      ResourceAccessManager accessManager,
      com.researchspace.dao.resourceaccess.ResourceAccessDao accessDao) {
    this.accessManager = accessManager;
    this.accessDao = accessDao;
  }

  @BeforeEach
  void createAuthorizationFixture() throws Exception {
    super.setUp();
    User subject = createAndSaveUser(getRandomName(10));
    User outgoingOwner = createAndSaveUser(getRandomName(10));
    User incomingOwner = createAndSaveUser(getRandomName(10));
    User groupCandidate = createAndSaveUser(getRandomName(10));
    User candidateGroupMember = createAndSaveUser(getRandomName(10));
    subjectUsername = subject.getUsername();
    outgoingOwnerUsername = outgoingOwner.getUsername();
    incomingOwnerUsername = incomingOwner.getUsername();
    groupCandidateUsername = groupCandidate.getUsername();
    candidateGroupMemberUsername = candidateGroupMember.getUsername();

    initUser(piUser);
    initUser(subject);
    initUser(groupCandidate);
    initUser(candidateGroupMember);
    Group managerGroup = createGroupForPiAndUsers(piUser, new User[] {subject, groupCandidate});
    groupId = managerGroup.getId();
    Group candidateGroup = createGroupForPiAndUsers(piUser, new User[] {candidateGroupMember});
    candidateGroupId = candidateGroup.getId();

    openTransaction();
    User persistedOwner = userMgr.getUserByUsername(outgoingOwnerUsername);
    Group persistedGroup = grpMgr.getGroup(groupId);
    ResourceAccess access =
        new ResourceAccess(ResourceAccessTestScheme.SCHEME_KEY, persistedOwner, new Date());
    access.addAssignment(
        ResourceRoleAssignment.forUser(ResourceAccessTestScheme.OWNER, persistedOwner));
    access.addAssignment(
        ResourceRoleAssignment.forGroup(ResourceAccessTestScheme.OWNER, persistedGroup));
    access.addAssignment(
        ResourceRoleAssignment.forAudience(
            ResourceAccessTestScheme.EDITOR, ResourceAudience.ALL_USERS, "All users"));
    sessionFactory.getCurrentSession().persist(access);
    sessionFactory.getCurrentSession().flush();
    resourceAccessId = access.getId();
    commitTransaction();
  }

  @Test
  void assignmentDocumentsAndPageResolutionUseConstantQueries() throws Exception {
    List<String> extraUsers = new ArrayList<>();
    List<Long> extraGroups = new ArrayList<>();
    for (int index = 0; index < 4; index++) {
      User member = createAndSaveUser(getRandomName(10));
      initUser(member);
      extraUsers.add(member.getUsername());
      extraGroups.add(createGroupForPiAndUsers(piUser, new User[] {member}).getId());
    }
    long smallCount = documentQueryCount();
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            transaction -> {
              ResourceAccess access =
                  sessionFactory.getCurrentSession().find(ResourceAccess.class, resourceAccessId);
              for (int index = 0; index < extraUsers.size(); index++) {
                access.addAssignment(
                    ResourceRoleAssignment.forUser(
                        ResourceAccessTestScheme.VIEWER,
                        userMgr.getUserByUsername(extraUsers.get(index))));
                access.addAssignment(
                    ResourceRoleAssignment.forGroup(
                        ResourceAccessTestScheme.VIEWER, grpMgr.getGroup(extraGroups.get(index))));
              }
              accessDao.flush();
            });
    assertEquals(
        smallCount, documentQueryCount(), "document query count must not grow per assignee");
  }

  private long documentQueryCount() {
    return new TransactionTemplate(getTxMger())
        .execute(
            transaction -> {
              var session = sessionFactory.getCurrentSession();
              User owner = userMgr.getUserByUsername(outgoingOwnerUsername);
              // Caller memberships and roles are outside assignment loading.
              owner.getGroups().size();
              owner.getRoles().size();
              ResourceAccess access = session.find(ResourceAccess.class, resourceAccessId);
              var statistics = sessionFactory.getStatistics();
              boolean enabled = statistics.isStatisticsEnabled();
              statistics.setStatisticsEnabled(true);
              try {
                statistics.clear();
                ResourceAccessDocument document =
                    accessManager.get(
                        accessAdapter(resourceAccessId, access), resourceAccessId, owner);
                long count = statistics.getPrepareStatementCount();
                for (var assignment : document.assignments()) {
                  var grantee = assignment.grantee();
                  assertTrue(grantee.available());
                  var original =
                      access.getAssignments().stream()
                          .filter(row -> row.getGranteeKey().equals(grantee.key()))
                          .findFirst()
                          .orElseThrow();
                  assertEquals(original.getNameSnapshot(), grantee.name());
                  assertEquals(original.getDetailSnapshot(), grantee.detail());
                  assertEquals(original.getRoleKey(), assignment.role());
                  assertEquals(original.getGranteeKind(), grantee.kind());
                  switch (original.getGranteeKind()) {
                    case USER -> {
                      assertEquals(original.getUser().getId(), grantee.id());
                      assertEquals(
                          Optional.of(
                              original.getRoleKey().equals(ResourceAccessTestScheme.OWNER)
                                  ? ResourceAccessTestScheme.OWNER
                                  : ResourceAccessTestScheme.EDITOR),
                          grantee.effectiveRole());
                      if (original.getRoleKey().equals(ResourceAccessTestScheme.OWNER)) {
                        assertEquals(
                            List.of(
                                new com.researchspace.model.resourceaccess.ResourceRoleSource(
                                    com.researchspace.model.resourceaccess.ResourceRoleSourceKind
                                        .DIRECT,
                                    original.getRoleKey(),
                                    original.getGranteeKey(),
                                    original.getNameSnapshot()),
                                new com.researchspace.model.resourceaccess.ResourceRoleSource(
                                    com.researchspace.model.resourceaccess.ResourceRoleSourceKind
                                        .AUDIENCE,
                                    ResourceAccessTestScheme.EDITOR,
                                    "audience:all-users",
                                    "All users")),
                            grantee.roleSources());
                      } else {
                        assertEquals(
                            java.util.Set.of(
                                com.researchspace.model.resourceaccess.ResourceRoleSourceKind
                                    .DIRECT,
                                com.researchspace.model.resourceaccess.ResourceRoleSourceKind.GROUP,
                                com.researchspace.model.resourceaccess.ResourceRoleSourceKind
                                    .AUDIENCE),
                            grantee.roleSources().stream()
                                .map(
                                    com.researchspace.model.resourceaccess.ResourceRoleSource::kind)
                                .collect(Collectors.toSet()));
                      }
                    }
                    case GROUP -> {
                      assertEquals(original.getGroup().getId(), grantee.id());
                      assertEquals(Optional.empty(), grantee.effectiveRole());
                      assertEquals(List.of(), grantee.roleSources());
                    }
                    case AUDIENCE -> {
                      assertEquals(ResourceAudience.ALL_USERS.name(), grantee.id());
                      assertEquals(Optional.empty(), grantee.effectiveRole());
                      assertEquals(List.of(), grantee.roleSources());
                    }
                  }
                }
                session.clear();
                ResourceAccess fresh = session.find(ResourceAccess.class, resourceAccessId);
                statistics.clear();
                accessManager.resolveAll(List.of(fresh), owner);
                for (var row : fresh.getAssignments()) {
                  if (row.getUser() != null) row.getUser().isEnabled();
                  if (row.getGroup() != null) row.getGroup().getDisplayName();
                }
                assertEquals(
                    1L,
                    statistics.getPrepareStatementCount(),
                    "page assignment hydration is one query");
                return count;
              } finally {
                statistics.setStatisticsEnabled(enabled);
              }
            });
  }

  @Test
  void distinctResourcesCanGrantToEachOtherWithinTheSameGroup() throws Exception {
    Long[] ids =
        new TransactionTemplate(getTxMger())
            .execute(
                transaction -> {
                  User first = userMgr.getUserByUsername(subjectUsername);
                  User second = userMgr.getUserByUsername(groupCandidateUsername);
                  ResourceAccess left =
                      new ResourceAccess(ResourceAccessTestScheme.SCHEME_KEY, first, new Date());
                  ResourceAccess right =
                      new ResourceAccess(ResourceAccessTestScheme.SCHEME_KEY, second, new Date());
                  left.addAssignment(
                      ResourceRoleAssignment.forUser(ResourceAccessTestScheme.OWNER, first));
                  right.addAssignment(
                      ResourceRoleAssignment.forUser(ResourceAccessTestScheme.OWNER, second));
                  sessionFactory.getCurrentSession().persist(left);
                  // Keep the two assignment index ranges away from the same insert gap.
                  ResourceAccess separator =
                      new ResourceAccess(ResourceAccessTestScheme.SCHEME_KEY, first, new Date());
                  separator.addAssignment(
                      ResourceRoleAssignment.forUser(ResourceAccessTestScheme.OWNER, first));
                  sessionFactory.getCurrentSession().persist(separator);
                  sessionFactory.getCurrentSession().persist(right);
                  sessionFactory.getCurrentSession().flush();
                  return new Long[] {left.getId(), right.getId(), first.getId(), second.getId()};
                });
    var subjectsLocked = new java.util.concurrent.CountDownLatch(2);
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var left = executor.submit(() -> grantToPeer(ids[0], ids[2], ids[3], subjectsLocked));
      var right = executor.submit(() -> grantToPeer(ids[1], ids[3], ids[2], subjectsLocked));
      left.get(30, java.util.concurrent.TimeUnit.SECONDS);
      right.get(30, java.util.concurrent.TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS));
    }
  }

  private void grantToPeer(
      Long accessId,
      Long subjectId,
      Long peerId,
      java.util.concurrent.CountDownLatch subjectsLocked) {
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            transaction -> {
              // Lock through production code, so the test follows changes to production lock modes.
              var dao = accessDao;
              dao.resolveAvailable("user:" + subjectId, ResourceAccessTestScheme.OWNER, null);
              subjectsLocked.countDown();
              try {
                assertTrue(subjectsLocked.await(10, java.util.concurrent.TimeUnit.SECONDS));
              } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
              }
              User subject = sessionFactory.getCurrentSession().find(User.class, subjectId);
              ResourceAccess access =
                  sessionFactory.getCurrentSession().find(ResourceAccess.class, accessId);
              dao.lockAuthorizationFacts(access, subject);
              access.addAssignment(
                  dao.resolveAvailable("user:" + peerId, ResourceAccessTestScheme.EDITOR, null));
              dao.flush();
            });
  }

  @Test
  void revokedGroupOwnerCannotTransferOwnershipFromAStaleSession() {
    TransactionStatus authorizationTransaction = openTransaction();
    try {
      User staleSubject = userMgr.getUserByUsername(subjectUsername);
      assertTrue(
          staleSubject.getGroups().stream().anyMatch(group -> groupId.equals(group.getId())));
      ResourceAccess access =
          sessionFactory.getCurrentSession().find(ResourceAccess.class, resourceAccessId);
      Map<String, String> before = roles(access);

      revokeGroupMembership(staleSubject);

      assertTrue(
          staleSubject.getGroups().stream().anyMatch(group -> groupId.equals(group.getId())));
      User outgoingOwner = userMgr.getUserByUsername(outgoingOwnerUsername);
      User incomingOwner = userMgr.getUserByUsername(incomingOwnerUsername);
      ResourceAccessException error =
          assertThrows(
              ResourceAccessException.class,
              () ->
                  accessManager.transferDirectOwnership(
                      accessAdapter(resourceAccessId, access),
                      resourceAccessId,
                      outgoingOwner,
                      incomingOwner,
                      staleSubject,
                      staleSubject));

      assertEquals(ResourceAccessException.Reason.FORBIDDEN, error.reason());
      assertEquals(before, roles(access));
    } finally {
      getTxMger().rollback(authorizationTransaction);
    }
  }

  @Test
  void disabledDirectAssigneeCannotLeaveFromAStaleSession() {
    addDirectSubjectOwner();
    TransactionStatus authorizationTransaction = openTransaction();
    Map<String, String> before;
    try {
      User staleSubject = userMgr.getUserByUsername(subjectUsername);
      assertTrue(staleSubject.isEnabled());
      ResourceAccess access =
          sessionFactory.getCurrentSession().find(ResourceAccess.class, resourceAccessId);
      before = roles(access);

      TransactionTemplate disableSubject = new TransactionTemplate(getTxMger());
      disableSubject.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      disableSubject.executeWithoutResult(
          transaction -> {
            int updated =
                sessionFactory
                    .getCurrentSession()
                    .createNativeMutationQuery(
                        "UPDATE User SET account_enabled = false WHERE id = :subjectId")
                    .setParameter("subjectId", staleSubject.getId())
                    .executeUpdate();
            assertEquals(1, updated);
          });

      assertTrue(staleSubject.isEnabled());
      ResourceAccessException error =
          assertThrows(
              ResourceAccessException.class,
              () ->
                  accessManager.removeSelf(
                      accessAdapter(resourceAccessId, access),
                      new RemoveSelfResourceAccess<>(resourceAccessId),
                      staleSubject,
                      staleSubject));

      assertAuthorizationRejected(error);
      assertEquals(before, roles(access));
    } finally {
      getTxMger().rollback(authorizationTransaction);
    }
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            transaction -> {
              User disabledSubject = userMgr.getUserByUsername(subjectUsername);
              assertFalse(disabledSubject.isEnabled());
              ResourceAccess persistedAccess =
                  sessionFactory.getCurrentSession().find(ResourceAccess.class, resourceAccessId);
              assertEquals(before, roles(persistedAccess));
            });
  }

  @Test
  void revokedSysadminCannotTransferOwnershipFromAStaleSession() {
    promoteSubjectAndRemoveGroupOwnerAssignment();
    TransactionStatus authorizationTransaction = openTransaction();
    try {
      User staleSubject = userMgr.getUserByUsername(subjectUsername);
      assertTrue(staleSubject.hasSysadminRole());
      ResourceAccess access =
          sessionFactory.getCurrentSession().find(ResourceAccess.class, resourceAccessId);
      Map<String, String> before = roles(access);

      revokeSysadmin(staleSubject);

      assertTrue(staleSubject.hasSysadminRole());
      User outgoingOwner = userMgr.getUserByUsername(outgoingOwnerUsername);
      User incomingOwner = userMgr.getUserByUsername(incomingOwnerUsername);
      ResourceAccessException error =
          assertThrows(
              ResourceAccessException.class,
              () ->
                  accessManager.transferDirectOwnership(
                      accessAdapter(resourceAccessId, access),
                      resourceAccessId,
                      outgoingOwner,
                      incomingOwner,
                      staleSubject,
                      staleSubject));

      assertEquals(ResourceAccessException.Reason.FORBIDDEN, error.reason());
      assertEquals(before, roles(access));
    } finally {
      getTxMger().rollback(authorizationTransaction);
    }
  }

  @Test
  void revokedSysadminDirectOwnerCannotAssignAnOutOfScopeUserFromAStaleSession() {
    addDirectSubjectOwner();
    promoteSubjectAndRemoveGroupOwnerAssignment();
    TransactionStatus authorizationTransaction = openTransaction();
    try {
      User staleSubject = userMgr.getUserByUsername(subjectUsername);
      assertTrue(staleSubject.hasSysadminRole());
      ResourceAccess access =
          sessionFactory.getCurrentSession().find(ResourceAccess.class, resourceAccessId);
      Map<String, String> before = roles(access);

      revokeSysadmin(staleSubject);

      assertTrue(staleSubject.hasSysadminRole());
      User outOfScopeCandidate = userMgr.getUserByUsername(incomingOwnerUsername);
      ResourceAccessException error =
          assertThrows(
              ResourceAccessException.class,
              () ->
                  accessManager.replace(
                      accessAdapter(resourceAccessId, access),
                      commandAdding(access, outOfScopeCandidate),
                      staleSubject,
                      staleSubject));

      assertEquals(ResourceAccessException.Reason.FORBIDDEN, error.reason());
      assertEquals(before, roles(access));
    } finally {
      getTxMger().rollback(authorizationTransaction);
    }
  }

  @Test
  void revokedGroupMemberDirectOwnerCannotAssignAGroupPeerFromAStaleSession() {
    addDirectSubjectOwner();
    TransactionStatus authorizationTransaction = openTransaction();
    try {
      User staleSubject = userMgr.getUserByUsername(subjectUsername);
      assertTrue(
          staleSubject.getGroups().stream().anyMatch(group -> groupId.equals(group.getId())));
      ResourceAccess access =
          sessionFactory.getCurrentSession().find(ResourceAccess.class, resourceAccessId);
      Map<String, String> before = roles(access);

      revokeGroupMembership(staleSubject);

      assertTrue(
          staleSubject.getGroups().stream().anyMatch(group -> groupId.equals(group.getId())));
      User formerGroupPeer = userMgr.getUserByUsername(groupCandidateUsername);
      ResourceAccessException error =
          assertThrows(
              ResourceAccessException.class,
              () ->
                  accessManager.replace(
                      accessAdapter(resourceAccessId, access),
                      commandAdding(access, formerGroupPeer),
                      staleSubject,
                      staleSubject));

      assertEquals(ResourceAccessException.Reason.FORBIDDEN, error.reason());
      assertEquals(before, roles(access));
    } finally {
      getTxMger().rollback(authorizationTransaction);
    }
  }

  @Test
  void directOwnerCannotAssignAUserWhoseSharedMembershipWasRevokedAfterSnapshot() {
    addDirectSubjectOwner();
    TransactionStatus authorizationTransaction = openTransaction();
    try {
      User staleSubject = userMgr.getUserByUsername(subjectUsername);
      User staleCandidate = userMgr.getUserByUsername(groupCandidateUsername);
      assertTrue(
          staleCandidate.getGroups().stream().anyMatch(group -> groupId.equals(group.getId())));
      ResourceAccess access =
          sessionFactory.getCurrentSession().find(ResourceAccess.class, resourceAccessId);
      Map<String, String> before = roles(access);

      revokeGroupMembership(staleCandidate);

      assertTrue(
          staleCandidate.getGroups().stream().anyMatch(group -> groupId.equals(group.getId())));
      ResourceAccessException error =
          assertThrows(
              ResourceAccessException.class,
              () ->
                  accessManager.replace(
                      accessAdapter(resourceAccessId, access),
                      commandAdding(access, ResourceGranteeKeys.user(staleCandidate.getId())),
                      staleSubject,
                      staleSubject));

      assertAuthorizationRejected(error);
      assertEquals(before, roles(access));
    } finally {
      getTxMger().rollback(authorizationTransaction);
    }
  }

  @Test
  void sysadminDirectOwnerCannotAssignAGroupWhoseLastEnabledMemberWasDisabledAfterSnapshot() {
    addDirectSubjectOwner();
    promoteSubjectAndRemoveGroupOwnerAssignment();
    TransactionStatus authorizationTransaction = openTransaction();
    try {
      User staleSubject = userMgr.getUserByUsername(subjectUsername);
      User staleCandidateMember = userMgr.getUserByUsername(candidateGroupMemberUsername);
      assertTrue(staleCandidateMember.isEnabled());
      ResourceAccess access =
          sessionFactory.getCurrentSession().find(ResourceAccess.class, resourceAccessId);
      Map<String, String> before = roles(access);

      disableUser(staleCandidateMember);

      assertTrue(staleCandidateMember.isEnabled());
      ResourceAccessException error =
          assertThrows(
              ResourceAccessException.class,
              () ->
                  accessManager.replace(
                      accessAdapter(resourceAccessId, access),
                      commandAdding(access, ResourceGranteeKeys.group(candidateGroupId)),
                      staleSubject,
                      staleSubject));

      assertAuthorizationRejected(error);
      assertEquals(before, roles(access));
    } finally {
      getTxMger().rollback(authorizationTransaction);
    }
  }

  private void revokeGroupMembership(User staleSubject) {
    TransactionTemplate revokeMembership = new TransactionTemplate(getTxMger());
    revokeMembership.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    revokeMembership.executeWithoutResult(
        transaction -> {
          int removed =
              sessionFactory
                  .getCurrentSession()
                  .createNativeMutationQuery(
                      "DELETE FROM UserGroup WHERE user_id = :subjectId AND group_id = :groupId")
                  .setParameter("subjectId", staleSubject.getId())
                  .setParameter("groupId", groupId)
                  .executeUpdate();
          assertEquals(1, removed);
        });
  }

  private void revokeSysadmin(User staleSubject) {
    TransactionTemplate revokeSysadmin = new TransactionTemplate(getTxMger());
    revokeSysadmin.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    revokeSysadmin.executeWithoutResult(
        transaction -> {
          Long sysadminRoleId =
              sessionFactory
                  .getCurrentSession()
                  .createQuery(
                      "select role.id from Role role where role.name = :roleName", Long.class)
                  .setParameter("roleName", Constants.SYSADMIN_ROLE)
                  .getSingleResult();
          int removed =
              sessionFactory
                  .getCurrentSession()
                  .createNativeMutationQuery(
                      "DELETE FROM user_role WHERE user_id = :subjectId AND role_id = :roleId")
                  .setParameter("subjectId", staleSubject.getId())
                  .setParameter("roleId", sysadminRoleId)
                  .executeUpdate();
          assertEquals(1, removed);
        });
  }

  private ReplaceResourceAccess<Long> commandAdding(ResourceAccess access, User candidate) {
    return commandAdding(access, ResourceGranteeKeys.user(candidate.getId()));
  }

  private ReplaceResourceAccess<Long> commandAdding(ResourceAccess access, String granteeKey) {
    List<ResourceAccessGrant> assignments = new ArrayList<>();
    access
        .getAssignments()
        .forEach(
            assignment ->
                assignments.add(
                    new ResourceAccessGrant(assignment.getGranteeKey(), assignment.getRoleKey())));
    assignments.add(new ResourceAccessGrant(granteeKey, ResourceAccessTestScheme.EDITOR));
    return new ReplaceResourceAccess<>(resourceAccessId, access.getVersion(), assignments);
  }

  private void disableUser(User staleUser) {
    TransactionTemplate disableUser = new TransactionTemplate(getTxMger());
    disableUser.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    disableUser.executeWithoutResult(
        transaction -> {
          int updated =
              sessionFactory
                  .getCurrentSession()
                  .createNativeMutationQuery(
                      "UPDATE User SET account_enabled = false WHERE id = :userId")
                  .setParameter("userId", staleUser.getId())
                  .executeUpdate();
          assertEquals(1, updated);
        });
  }

  private static void assertAuthorizationRejected(ResourceAccessException error) {
    assertTrue(
        error.reason() == ResourceAccessException.Reason.FORBIDDEN
            || error.reason() == ResourceAccessException.Reason.STALE);
  }

  private void addDirectSubjectOwner() {
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            transaction -> {
              User subject = userMgr.getUserByUsername(subjectUsername);
              ResourceAccess access =
                  sessionFactory.getCurrentSession().find(ResourceAccess.class, resourceAccessId);
              access.addAssignment(
                  ResourceRoleAssignment.forUser(ResourceAccessTestScheme.OWNER, subject));
              sessionFactory.getCurrentSession().flush();
            });
  }

  private void promoteSubjectAndRemoveGroupOwnerAssignment() {
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            transaction -> {
              User subject = userMgr.getUserByUsername(subjectUsername);
              subject.addRole(roleMgr.getRole(Constants.SYSADMIN_ROLE));
              ResourceAccess access =
                  sessionFactory.getCurrentSession().find(ResourceAccess.class, resourceAccessId);
              ResourceRoleAssignment groupOwner =
                  access.getAssignments().stream()
                      .filter(
                          assignment ->
                              ResourceGranteeKeys.group(groupId).equals(assignment.getGranteeKey()))
                      .findFirst()
                      .orElseThrow();
              access.removeAssignment(groupOwner);
              sessionFactory.getCurrentSession().flush();
            });
  }

  private static ProtectedResourceAccess<ResourceAccess, Long> accessAdapter(
      Long id, ResourceAccess access) {
    return new ProtectedResourceAccess<>() {
      @Override
      public Optional<ResourceAccess> find(Long requestedId) {
        return id.equals(requestedId) ? Optional.of(access) : Optional.empty();
      }

      @Override
      public Optional<ResourceAccess> lock(Long requestedId) {
        return find(requestedId);
      }

      @Override
      public ResourceAccess access(ResourceAccess resource) {
        return resource;
      }

      @Override
      public String viewAccessCapability() {
        return ResourceAccessTestScheme.READ_RESOURCE;
      }

      @Override
      public String manageAssignmentsCapability() {
        return ResourceAccessTestScheme.MANAGE_ASSIGNMENTS;
      }

      @Override
      public String manageOwnersCapability() {
        return ResourceAccessTestScheme.MANAGE_OWNERS;
      }
    };
  }

  public static class SchemeInitializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
      context
          .getBeanFactory()
          .registerSingleton("resourceAccessTestScheme", new ResourceAccessTestScheme());
    }
  }

  private static Map<String, String> roles(ResourceAccess access) {
    return access.getAssignments().stream()
        .collect(
            Collectors.toMap(
                ResourceRoleAssignment::getGranteeKey, ResourceRoleAssignment::getRoleKey));
  }
}
