package com.researchspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.Constants;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import com.researchspace.testutils.TestGroup;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

public class CommunityServiceManagerIT extends RealTransactionSpringTestBase {

  private @Autowired SystemPropertyManager sysPropMgr;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  // check that collections are preloaded and no lazy-loading exception
  @Test
  public void testGetCommunityWithAdminsAndGroups() throws IllegalAddChildOperation {
    TestGroup testGroup = createTestGroup(0);
    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    initUsers(admin);
    TestCommunity testcommunity = createTestCommunity(TransformerUtils.toSet(testGroup), admin);

    Community comm =
        communityMgr.getCommunityWithAdminsAndGroups(testcommunity.getCommunity().getId());
    assertThat(comm.getAdmins()).contains(admin);
    assertThat(comm.getLabGroups()).contains(testGroup.getGroup());
    // missed object return null
    assertNull(communityMgr.getCommunityWithAdminsAndGroups(-200L)); // unknown ID
  }

  @Test
  public void testRemoveCommunity() throws IllegalAddChildOperation {
    PaginationCriteria<Community> pgcrit =
        PaginationCriteria.createDefaultForClass(Community.class);
    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    initUsers(admin);
    logoutAndLoginAs(admin);
    long initialTotal = communityMgr.listCommunities(admin, pgcrit).getTotalHits();
    // can't remove default community
    assertFalse(communityMgr.removeCommunity(Community.DEFAULT_COMMUNITY_ID).isSucceeded());
    assertEquals(initialTotal, getCommunityCount(pgcrit, admin));

    Community community = createAndSaveCommunity(admin, "toRemove1");
    assertEquals(initialTotal + 1, getCommunityCount(pgcrit, admin));
    assertThat(userMgr.getAvailableAdminUsers()).doesNotContain(admin);
    setASystemPropertyForCommunity(community, admin);
    // now remove this community, should remove admins as well
    communityMgr.removeCommunity(community.getId());
    assertEquals(initialTotal, getCommunityCount(pgcrit, admin));
    // admin should be available again
    assertThat(userMgr.getAvailableAdminUsers()).contains(admin);
  }

  @Test
  public void removeCommunityMovesLabGroupsToDefaultCommunity() throws IllegalAddChildOperation {
    TestGroup testGroup = createTestGroup(0);
    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    initUsers(admin);
    Long groupId = testGroup.getGroup().getId();
    Long piId = testGroup.getPi().getId();
    Long communityId =
        createTestCommunity(TransformerUtils.toSet(testGroup), admin).getCommunity().getId();
    assertThat(communityMgr.listCommunitiesForUser(piId))
        .extracting(Community::getId)
        .containsExactly(communityId);

    logoutAndLoginAsSysAdmin();
    assertTrue(communityMgr.removeCommunity(communityId).isSucceeded());

    Group grp = grpMgr.getGroupWithCommunities(groupId);
    assertEquals(Community.DEFAULT_COMMUNITY_ID, grp.getCommunity().getId());
    assertEquals(Community.DEFAULT_COMMUNITY_ID, grp.getCommunityId());
    assertThat(
            communityMgr
                .getCommunityWithAdminsAndGroups(Community.DEFAULT_COMMUNITY_ID)
                .getLabGroups())
        .extracting(Group::getId)
        .contains(groupId);
    assertThat(communityMgr.listCommunitiesForUser(piId))
        .extracting(Community::getId)
        .containsExactly(Community.DEFAULT_COMMUNITY_ID);
  }

  @Test
  public void groupAssignedWhileTheCommunityIsBeingRemovedIsMovedToDefaultCommunity()
      throws Exception {
    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    initUsers(admin);
    Long communityId =
        createTestCommunity(TransformerUtils.toSet(createTestGroup(0)), admin)
            .getCommunity()
            .getId();
    Long lateGroupId = createTestGroup(0).getGroup().getId();

    // the assignment runs as the community admin and stays uncommitted while the removal starts
    logoutAndLoginAs(admin);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    TransactionStatus assignment = openTransaction();
    try {
      communityMgr.addGroupToCommunity(lateGroupId, communityId, admin);
      Future<Boolean> removal =
          pool.submit(() -> communityMgr.removeCommunity(communityId).isSucceeded());
      Thread.sleep(1000);
      assertFalse(removal.isDone(), "removal should wait for the assignment to commit");

      getTxMger().commit(assignment);
      assertTrue(removal.get(30, TimeUnit.SECONDS));
    } finally {
      if (!assignment.isCompleted()) {
        getTxMger().rollback(assignment);
      }
      pool.shutdownNow();
    }

    Group lateGroup = grpMgr.getGroupWithCommunities(lateGroupId);
    assertEquals(Community.DEFAULT_COMMUNITY_ID, lateGroup.getCommunity().getId());
    assertEquals(Community.DEFAULT_COMMUNITY_ID, lateGroup.getCommunityId());
  }

  @Test
  public void communityListReadBeforeTheRemovalCommitsIsNotCachedAfterIt() throws Exception {
    TestGroup testGroup = createTestGroup(0);
    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    initUsers(admin);
    Long piId = testGroup.getPi().getId();
    Long communityId =
        createTestCommunity(TransformerUtils.toSet(testGroup), admin).getCommunity().getId();

    CountDownLatch removed = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<?> removal =
          pool.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          tx -> {
                            communityMgr.removeCommunity(communityId);
                            removed.countDown();
                            awaitQuietly(release);
                          }));
      assertTrue(removed.await(30, TimeUnit.SECONDS));
      // reads the committed membership, which still has the community, and caches it
      assertThat(communityMgr.listCommunitiesForUser(piId))
          .extracting(Community::getId)
          .containsExactly(communityId);

      release.countDown();
      removal.get(30, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      pool.shutdownNow();
    }

    assertThat(communityMgr.listCommunitiesForUser(piId))
        .extracting(Community::getId)
        .containsExactly(Community.DEFAULT_COMMUNITY_ID);
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void setASystemPropertyForCommunity(Community community, User subject) {
    sysPropMgr.listSystemPropertyDefinitions().stream()
        .filter(sp -> sp.getName().equals("slack.available"))
        .findFirst()
        .ifPresent(
            systemProperty ->
                sysPropMgr.save(
                    new SystemPropertyValue(
                        systemProperty, HierarchicalPermission.DENIED.name(), community),
                    subject));
  }

  private int getCommunityCount(PaginationCriteria<Community> pgcrit, User admin) {
    return communityMgr.listCommunities(admin, pgcrit).getTotalHits().intValue();
  }

  // check that collections are preloaded and no lazy-loading exception
  @Test
  public void testAddGroupToCommunity() throws IllegalAddChildOperation {
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    initUsers(pi, admin);
    Group grp = createGroupForUsers(pi, pi.getUsername(), "", pi);

    Community community1 = createAndSaveCommunity(admin, "id2");
    User sysadmin = logoutAndLoginAsSysAdmin();
    community1 = communityMgr.addGroupToCommunity(grp.getId(), community1.getId(), sysadmin);
    assertThat(community1.getLabGroups()).contains(grp);

    Community community2 = createAndSaveCommunity(admin, "id3");
    community2 = communityMgr.addGroupToCommunity(grp.getId(), community2.getId(), sysadmin);
    assertThat(community2.getLabGroups()).contains(grp);

    // community1 has no groups
    community1 = communityMgr.getCommunityWithAdminsAndGroups(community1.getId());
    assertThat(community1.getLabGroups()).isEmpty();
  }

  @Test
  public void testCommunityEditPermissionsForAdmin() {
    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    User admin2 = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    Community defaultComm = communityMgr.get(Community.DEFAULT_COMMUNITY_ID);
    logoutAndLoginAs(admin);
    openTransaction();
    assertFalse(permissionUtils.isPermitted(defaultComm, PermissionType.WRITE, admin));
    commitTransaction();
    logoutAndLoginAsSysAdmin();
    communityMgr.addAdminsToCommunity(
        new Long[] {admin.getId(), admin2.getId()}, Community.DEFAULT_COMMUNITY_ID);
    logoutAndLoginAs(admin);
    permissionUtils.refreshCacheIfNotified();
    openTransaction();
    assertTrue(permissionUtils.isPermitted(defaultComm, PermissionType.WRITE, admin));
    commitTransaction();
    logoutAndLoginAsSysAdmin();

    // and should be gone if removed
    communityMgr.removeAdminFromCommunity(admin.getId(), Community.DEFAULT_COMMUNITY_ID);
    logoutAndLoginAs(admin);
    permissionUtils.refreshCacheIfNotified();
    openTransaction();
    assertFalse(permissionUtils.isPermitted(defaultComm, PermissionType.WRITE, admin));
    commitTransaction();
  }

  // RSPAC-426 testfix
  @Test
  public void testAdminCanReadDocsBelongingToCommunityMembers() throws IllegalAddChildOperation {
    User pi = createAndSaveUser(getRandomAlphabeticString("any"), Constants.PI_ROLE);
    User otheruser = createAndSaveUser(getRandomAlphabeticString("any"));
    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    User otheradmin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    initUsers(pi, otheruser);
    User sysadmin = logoutAndLoginAsSysAdmin();
    Community community1 = createAndSaveCommunity(admin, "comm1");
    Group grp = createGroupForUsers(pi, pi.getUsername(), "", pi);
    community1 = communityMgr.addGroupToCommunity(grp.getId(), community1.getId(), sysadmin);
    grp = community1.getLabGroups().iterator().next();
    logoutAndLoginAs(pi);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(pi, "any");
    logoutAndLoginAs(otheradmin);
    // otheradmin can't see u's docs, as otheradmin is not a community admin yet
    assertFalse(permissionUtils.isPermitted(doc, PermissionType.READ, otheradmin));
    // nor can he edit group
    assertFalse(permissionUtils.isPermitted(grp, PermissionType.WRITE, otheradmin));
    // now, admin is added to community
    communityMgr.addAdminsToCommunity(new Long[] {otheradmin.getId()}, community1.getId());

    logoutAndLoginAs(otheradmin);

    // admin can see u's docs, as us is now in community
    // refresh doc for new group membership
    doc = recordMgr.get(doc.getId()).asStrucDoc();
    otheradmin = userMgr.get(otheradmin.getId());
    assertTrue(permissionUtils.isPermitted(doc, PermissionType.READ, otheradmin));
    assertTrue(permissionUtils.isPermitted(grp, PermissionType.WRITE, otheradmin));

    logoutAndLoginAsSysAdmin();

    // now let's remove the admin, he can no longer see the user's document
    // or edit a group in the community, RSPAC-73
    communityMgr.removeAdminFromCommunity(otheradmin.getId(), community1.getId());
    logoutAndLoginAs(otheradmin);
    otheradmin = userMgr.get(otheradmin.getId());
    assertFalse(permissionUtils.isPermitted(doc, PermissionType.READ, otheradmin));
    assertFalse(permissionUtils.isPermitted(grp, PermissionType.WRITE, otheradmin));

    sysadmin = logoutAndLoginAsSysAdmin();
    // now, let's add back the admin as a community admin
    communityMgr.addAdminsToCommunity(new Long[] {otheradmin.getId()}, community1.getId());
    // let's remove the group from the community by moving it to another
    communityMgr.addGroupToCommunity(grp.getId(), Community.DEFAULT_COMMUNITY_ID, sysadmin);
    // since the user is no longer in the community, otheradmin can't see him
    logoutAndLoginAs(otheradmin);
    // refresh objects to get current version
    otheradmin = userMgr.get(otheradmin.getId());
    doc = recordMgr.get(doc.getId()).asStrucDoc();
    assertFalse(permissionUtils.isPermitted(doc, PermissionType.READ, otheradmin));
    // we 'll add group back to original community that otheradmin is in
    communityMgr.addGroupToCommunity(grp.getId(), community1.getId(), sysadmin);

    logoutAndLoginAs(otheruser);
    StructuredDocument otherDoc = createBasicDocumentInRootFolderWithText(otheruser, "any");

    logoutAndLoginAs(otheradmin);
    // user is not in group; admin can't see
    assertFalse(permissionUtils.isPermitted(otherDoc, PermissionType.READ, otheradmin));
    // now, let's add a new user to this group:
    // now we add to group, and hence admin will be able to see his doxc
    grpMgr.addUserToGroup(otheruser.getUsername(), grp.getId(), RoleInGroup.DEFAULT);
    // refresh otherDoc
    otherDoc = recordMgr.get(otherDoc.getId()).asStrucDoc();
    assertTrue(permissionUtils.isPermitted(otherDoc, PermissionType.READ, otheradmin));
  }

  @Test
  public void testAddRemoveAdminToCommunity() throws IllegalAddChildOperation {

    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    User otheradmin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    initUsers(admin, otheradmin);

    Community community1 = createAndSaveCommunity(admin, "add1");
    Community community2 = createAndSaveCommunity(otheradmin, "add2");

    // can't remove only admin
    assertFalse(
        communityMgr.removeAdminFromCommunity(admin.getId(), community1.getId()).isSucceeded());
    // can't add otheradmin, since they already belong to a community
    Community altered =
        communityMgr.addAdminsToCommunity(new Long[] {otheradmin.getId()}, community1.getId());
    assertThat(altered.getAdmins()).hasSize(1);
    // so we create a 3rd admin
    User otheradmin3 = createAndSaveUser(getRandomAlphabeticString("admin3"), Constants.ADMIN_ROLE);
    altered =
        communityMgr.addAdminsToCommunity(new Long[] {otheradmin3.getId()}, community1.getId());

    assertThat(altered.getAdmins()).hasSize(2);
    // now we can remove the original admin, since there are now 2.
    assertThat(userMgr.getAvailableAdminUsers()).doesNotContain(admin);
    assertTrue(
        communityMgr.removeAdminFromCommunity(admin.getId(), community1.getId()).isSucceeded());

    // admin should be available again
    assertThat(userMgr.getAvailableAdminUsers()).contains(admin);
  }
}
