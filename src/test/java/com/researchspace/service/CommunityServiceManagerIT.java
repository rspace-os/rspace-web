package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class CommunityServiceManagerIT extends RealTransactionSpringTestBase {

  private @Autowired SystemPropertyManager sysPropMgr;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
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
    assertTrue(comm.getAdmins().contains(admin));
    assertTrue(comm.getLabGroups().contains(testGroup.getGroup()));
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
    assertFalse(userMgr.getAvailableAdminUsers().contains(admin));
    setASystemPropertyForCommunity(community, admin);
    // now remove this community, should remove admins as well
    communityMgr.removeCommunity(community.getId());
    assertEquals(initialTotal, getCommunityCount(pgcrit, admin));
    // admin should be available again
    assertTrue(userMgr.getAvailableAdminUsers().contains(admin));
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
    assertTrue(community1.getLabGroups().contains(grp));

    Community community2 = createAndSaveCommunity(admin, "id3");
    community2 = communityMgr.addGroupToCommunity(grp.getId(), community2.getId(), sysadmin);
    assertTrue(community2.getLabGroups().contains(grp));

    // community1 has no groups
    community1 = communityMgr.getCommunityWithAdminsAndGroups(community1.getId());
    assertEquals(0, community1.getLabGroups().size());
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
    assertEquals(1, altered.getAdmins().size());
    // so we create a 3rd admin
    User otheradmin3 = createAndSaveUser(getRandomAlphabeticString("admin3"), Constants.ADMIN_ROLE);
    altered =
        communityMgr.addAdminsToCommunity(new Long[] {otheradmin3.getId()}, community1.getId());

    assertEquals(2, altered.getAdmins().size());
    // now we can remove the original admin, since there are now 2.
    assertFalse(userMgr.getAvailableAdminUsers().contains(admin));
    assertTrue(
        communityMgr.removeAdminFromCommunity(admin.getId(), community1.getId()).isSucceeded());

    // admin should be available again
    assertTrue(userMgr.getAvailableAdminUsers().contains(admin));
  }
}
