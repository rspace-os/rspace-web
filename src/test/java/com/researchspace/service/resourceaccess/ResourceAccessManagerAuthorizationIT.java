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

  private String subjectUsername;
  private String outgoingOwnerUsername;
  private String incomingOwnerUsername;
  private String groupCandidateUsername;
  private String candidateGroupMemberUsername;
  private Long groupId;
  private Long candidateGroupId;
  private Long resourceAccessId;

  @Autowired
  ResourceAccessManagerAuthorizationIT(ResourceAccessManager accessManager) {
    this.accessManager = accessManager;
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
