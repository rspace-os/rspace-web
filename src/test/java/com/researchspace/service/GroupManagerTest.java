package com.researchspace.service;

import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.service.UserFolderCreator.SHARED_SNIPPETS_FOLDER_PREFIX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.Constants;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.Community;
import com.researchspace.model.DefaultGroupNamingStrategy;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.model.dtos.GroupSearchCriteria;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.ConstraintPermissionResolver;
import com.researchspace.model.permissions.FormPermissionAdapter;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionTestUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.permissions.RecordPermissionAdapter;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.system.SystemProperty;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestGroup;
import java.util.Arrays;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class GroupManagerTest extends SpringTransactionalTest {

  @Autowired SystemPropertyManager systemPropertyManager;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void testAddRemoveGrp() {
    User u1 = createAndSaveUserIfNotExists("any");
    Group g1 = new Group("unique", u1);
    grpMgr.saveGroup(g1, u1);

    assertNotNull(g1.getId());

    Group g1reloaded = grpMgr.getGroup(g1.getId());
    assertEquals(g1, g1reloaded);
    // should be associated with default comunity
    assertTrue(
        communityMgr.get(Community.DEFAULT_COMMUNITY_ID).getLabGroups().contains(g1reloaded));
    assertEquals(Community.DEFAULT_COMMUNITY_ID, g1reloaded.getCommunity().getId());
  }

  @Test
  public void testAlterRole() throws Exception {

    TestGroup testgroup = createTestGroup(0);
    Group group = testgroup.getGroup();

    User sysadmin = logoutAndLoginAsSysAdmin();
    // can't remove only sysadmin
    assertExceptionThrown(
        () ->
            grpMgr.setRoleForUser(
                group.getId(),
                testgroup.getPi().getId(),
                RoleInGroup.RS_LAB_ADMIN.name(),
                sysadmin),
        IllegalStateException.class);

    User pi2 = createAndSaveUserIfNotExists("pi2", Constants.PI_ROLE);
    User member = createAndSaveUserIfNotExists("member");
    initialiseContentWithEmptyContent(pi2, member);

    User pi = testgroup.getPi();
    // add second pi and a regular member
    addUsersToGroup(testgroup.getPi(), group, pi2, member);
    logoutAndLoginAsSysAdmin();
    grpMgr.setRoleForUser(group.getId(), pi2.getId(), RoleInGroup.PI.name(), sysadmin);

    /* pis can see group and member's folder, but not other pi's */
    assertEquals(2, folderDao.getLabGroupFolderForUser(pi).getChildren().size());

    // demote pi to normal user - can only see group's folder
    grpMgr.setRoleForUser(group.getId(), pi.getId(), RoleInGroup.DEFAULT.name(), sysadmin);
    assertEquals(1, folderDao.getLabGroupFolderForUser(pi).getChildren().size());

    // promote to Lab Admin - still only see group's folder
    grpMgr.setRoleForUser(group.getId(), pi.getId(), RoleInGroup.RS_LAB_ADMIN.name(), sysadmin);
    assertEquals(1, folderDao.getLabGroupFolderForUser(pi).getChildren().size());

    logoutAndLoginAs(pi2);
    // add 'view all' lab admin permission - can see group's and member's folder
    grpMgr.authorizeLabAdminToViewAll(pi.getId(), pi2, group.getId(), true);
    assertEquals(2, folderDao.getLabGroupFolderForUser(pi).getChildren().size());

    // remove 'view all' lab admin permission - can only see group's folder
    grpMgr.authorizeLabAdminToViewAll(pi.getId(), pi2, group.getId(), false);
    assertEquals(1, folderDao.getLabGroupFolderForUser(pi).getChildren().size());

    // add 'view all' lab admin permission again, then remove from the group (RSPAC-1116)
    grpMgr.authorizeLabAdminToViewAll(pi.getId(), pi2, group.getId(), true);
    assertEquals(2, folderDao.getLabGroupFolderForUser(pi).getChildren().size());
    grpMgr.removeUserFromGroup(pi.getUsername(), group.getId(), pi2);
    // no user nor group folder
    assertEquals(0, folderDao.getLabGroupFolderForUser(pi).getChildren().size());
  }

  @Test
  public void testAddRemoveUser() {

    // create and save a user
    User u1 = TestFactory.createAnyUser("u1");

    userDao.save(u1);
    Group g1 = new Group("unique", u1);

    g1.addMember(u1, RoleInGroup.DEFAULT);
    assertTrue(u1.hasGroup(g1));
    assertTrue(g1.hasMember(u1));
    grpMgr.saveGroup(g1, u1);

    Group g1reloaded = grpMgr.getGroup(g1.getId());

    assertEquals(u1, g1reloaded.getMembers().iterator().next());
    assertTrue(g1reloaded.hasMember(u1));
    User loaded = g1reloaded.getMembers().iterator().next();
    assertTrue(loaded.hasGroup(g1reloaded));

    g1reloaded.removeMember(u1);
    grpMgr.saveGroup(g1reloaded, u1);

    Group g1reloaded2 = grpMgr.getGroup(g1.getId());
    assertFalse(g1reloaded2.hasMember(u1));
  }

  public void evictCurrentObjects(Group g1, Group g2, User u1) {
    sessionFactory.getCurrentSession().evict(u1);
    for (UserGroup ug : g1.getUserGroups()) {
      sessionFactory.getCurrentSession().evict(ug);
    }
    for (UserGroup ug : g2.getUserGroups()) {
      sessionFactory.getCurrentSession().evict(ug);
    }
  }

  @Test
  public void testAddRemovePermissions() {
    User user = createAndSaveUserIfNotExists("u1");
    // set up a group with a permission
    Group g1 = new Group("unique", user);
    ConstraintPermissionResolver cpr = new ConstraintPermissionResolver();
    String permssionStr = PermissionDomain.RECORD + ":" + PermissionType.READ + ":id=1,2,3";
    ConstraintBasedPermission permOb = cpr.resolvePermission(permssionStr);
    g1.addPermission(permOb);
    grpMgr.saveGroup(g1, user);

    // now test reloaded
    Group g2 = grpMgr.getGroup(g1.getId());
    assertEquals(1, g2.getPermissions().size());
    ConstraintBasedPermission reloaded =
        (ConstraintBasedPermission) g2.getPermissions().iterator().next();
    PermissionTestUtils.assertPermissionsAreEquivalent(reloaded, permOb);

    // now remove and test
    g2.removePermission(reloaded);
    grpMgr.saveGroup(g2, user);
    Group g3 = grpMgr.getGroup(g1.getId());
    assertTrue(g3.getPermissions().isEmpty());
  }

  @Test
  public void testMemberAcquiresPermissionsForRole() throws Exception {
    User pi = createAndSaveUserIfNotExists("pi", Constants.PI_ROLE);
    Group grp1 = new Group("g1", pi);
    grp1.setPis("pi"); // simulate from ui
    grpMgr.saveGroup(grp1, pi);
    User user = createAndSaveUserIfNotExists("groupmember");
    initialiseContentWithEmptyContent(user);
    initialiseContentWithEmptyContent(pi);
    grpMgr.addMembersToGroup(grp1.getId(), Arrays.asList(new User[] {user, pi}), "pi", "", user);

    pi = userDao.get(pi.getId());

    Record toTest = TestFactory.createAnySD();
    toTest.setOwner(user);
    // check can read/export all records
    RecordPermissionAdapter rpa = new RecordPermissionAdapter(toTest);
    rpa.setAction(PermissionType.READ);
    grp1 = grpMgr.getGroup(grp1.getId());
    for (User u : grp1.getMembers()) {
      if (u.equals(pi)) {
        assertTrue(pi.isPermitted(rpa, true));
      }
    }

    rpa.setAction(PermissionType.EXPORT);
    assertTrue(pi.isPermitted(rpa, true));

    RSForm form = TestFactory.createAnyForm("temp1");
    form.setOwner(user);
    // check can read/export all tempaltes
    FormPermissionAdapter tpa = new FormPermissionAdapter(form);
    tpa.setAction(PermissionType.WRITE);
    assertTrue(pi.isPermitted(tpa, true));
    tpa.setAction(PermissionType.READ);
    assertTrue(pi.isPermitted(tpa, true));
  }

  @Test
  public void testAddMembersToGroupOwner_AssignsGroupOwnerRoleToCreator() {
    User groupOwner = createAndSaveUserIfNotExists("owner", Constants.USER_ROLE);
    Group grp1 = new Group("g1", groupOwner);
    grp1.setGroupType(GroupType.PROJECT_GROUP);
    User user = createAndSaveUserIfNotExists("groupmember");

    grp1.setOwner(groupOwner);
    grpMgr.saveGroup(grp1, groupOwner);
    Group savedGroup =
        grpMgr.addMembersToProjectGroup(
            grp1.getId(), Arrays.asList(user, groupOwner), "owner", groupOwner);
    assertEquals("owner", savedGroup.getGroupOwners());
  }

  @Test
  public void testAddMembersToGroupOwner_AllowsMultipleGroupOwners() {
    User groupOwner = createAndSaveUserIfNotExists("owner", Constants.USER_ROLE);
    Group grp1 = new Group("g1", groupOwner);
    grp1.setGroupType(GroupType.PROJECT_GROUP);
    User user = createAndSaveUserIfNotExists("groupmember", Constants.ADMIN_ROLE);

    grp1.setOwner(groupOwner);
    grpMgr.saveGroup(grp1, groupOwner);
    Group savedGroup =
        grpMgr.addMembersToProjectGroup(
            grp1.getId(), Arrays.asList(user, groupOwner), "owner,groupmember", groupOwner);
    assertEquals("owner,groupmember", savedGroup.getGroupOwners());
  }

  @Test
  public void testAddMembersToGroupOwner_AssignsUserRoleToAllOtherUsers() {
    User groupOwner = createAndSaveUserIfNotExists("owner", Constants.PI_ROLE);
    Group grp1 = new Group("g1", groupOwner);
    grp1.setGroupType(GroupType.PROJECT_GROUP);
    User admin = createAndSaveUserIfNotExists("groupmember", Constants.ADMIN_ROLE);
    User regularUser = createAndSaveUserIfNotExists("normalUser");

    grp1.setOwner(groupOwner);
    grpMgr.saveGroup(grp1, groupOwner);
    Group savedGroup =
        grpMgr.addMembersToProjectGroup(
            grp1.getId(), Arrays.asList(admin, groupOwner, regularUser), "owner", groupOwner);
    assertEquals(RoleInGroup.DEFAULT, savedGroup.getRoleForUser(admin));
    assertEquals(RoleInGroup.DEFAULT, savedGroup.getRoleForUser(regularUser));
  }

  @Test
  public void testListGroupsForVariousRoles() throws IllegalAddChildOperation {
    User pi1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi1"), Constants.PI_ROLE);
    User pi2 = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi2"), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(pi1);
    initialiseContentWithEmptyContent(pi2);
    logoutAndLoginAsSysAdmin();

    PaginationCriteria<Group> pgCrit = PaginationCriteria.createDefaultForClass(Group.class);
    final int INITIAL_GRP_COUNT = getGroupCount(pgCrit);
    Group g1 = createGroup("pi1Group", pi1);
    Group g2 = createGroup("pi2Group", pi2);

    GroupSearchCriteria srchCrit = new GroupSearchCriteria();
    srchCrit.setFilterByCommunity(true);
    pgCrit.setSearchCriteria(srchCrit);
    // sysadmin gets all groups
    assertEquals(INITIAL_GRP_COUNT + 2, getGroupCount(pgCrit));
    // create a community with an admin
    User communityAdmin = createAndSaveAdminUser();
    Community comm = createAndSaveCommunity(communityAdmin, "any.id");
    // now log on as community admin, can see no groups
    logoutAndLoginAs(communityAdmin);
    assertEquals(0, grpMgr.list(communityAdmin, pgCrit).getResults().size());
    communityMgr.addGroupToCommunity(g1.getId(), comm.getId(), communityAdmin);
    // now can see the added group
    assertEquals(1, grpMgr.list(communityAdmin, pgCrit).getResults().size());
  }

  @Test
  public void testGroupListingForPrivateProfileGroups() {
    User pi1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi1"), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(pi1);
    logoutAndLoginAs(pi1);

    // get initial group number
    PaginationCriteria<Group> pgCrit = PaginationCriteria.createDefaultForClass(Group.class);
    final int INITIAL_GRP_COUNT = getGroupCount(pgCrit);

    // create new group and hide it from public listings
    Group newGroup = createGroup("pi1Group", pi1);
    newGroup = addUsersToGroup(pi1, newGroup);
    grpMgr.hideGroupProfile(true, newGroup, pi1);

    // should be listed with default search criteria
    GroupSearchCriteria srchCrit = new GroupSearchCriteria();
    pgCrit.setSearchCriteria(srchCrit);
    pgCrit.setOrderBy("id");
    assertEquals(INITIAL_GRP_COUNT + 1, getGroupCount(pgCrit));

    // should be hidden if search criteria ask for public profiles only
    srchCrit.setOnlyPublicProfiles(true);
    assertEquals(INITIAL_GRP_COUNT, getGroupCount(pgCrit));
  }

  @Test
  public void ensurePiAndLabAdminCanModifyGroupPrivateProfileSetting() {

    final User labGroupPI = createAndSaveAPi();
    final User labAdmin = createAndSaveRandomUser();
    final User user = createAndSaveRandomUser();

    initialiseContentWithEmptyContent(labGroupPI, labAdmin, user);

    // create new group with private profile
    Group group = createGroup("pi1Group", labGroupPI);
    addUsersToGroup(labGroupPI, group, labAdmin, user);
    logoutAndLoginAs(labGroupPI);
    grpMgr.setRoleForUser(
        group.getId(), labAdmin.getId(), RoleInGroup.RS_LAB_ADMIN.name(), labGroupPI);

    // initially group is public
    assertFalse(group.isPrivateProfile());

    // pi can hide the group
    group = grpMgr.hideGroupProfile(true, group, labGroupPI);
    assertTrue(group.isPrivateProfile());

    // lab admin can change the setting
    group = grpMgr.hideGroupProfile(false, group, labAdmin);
    assertFalse(group.isPrivateProfile());

    // member not allowed to change group status
    try {
      group = grpMgr.hideGroupProfile(false, group, user);
      fail("should throw authorization exception");
    } catch (AuthorizationException ae) {
      // expected
    }
  }

  int getGroupCount(PaginationCriteria<Group> pgCrit) {
    return grpMgr.list(getSysAdminUser(), pgCrit).getResults().size();
  }

  @Test
  public void testSetUpGroupFolder() throws IllegalAddChildOperation {
    User admin = createAndSaveUserIfNotExists(CoreTestUtils.getRandomName(10));
    User user = createAndSaveUserIfNotExists(CoreTestUtils.getRandomName(10));
    Group g1 = new Group(CoreTestUtils.getRandomName(10), admin);
    g1 = grpMgr.saveGroup(g1, admin);
    initialiseContentWithEmptyContent(user, admin);

    g1 =
        grpMgr.addMembersToGroup(
            g1.getId(), Arrays.asList(new User[] {user, admin}), "", "admin2", admin);
    Folder grpFolder = grpMgr.createSharedCommunalGroupFolders(g1.getId(), admin.getUsername());
    String expectedName = new DefaultGroupNamingStrategy().getSharedGroupName(g1);
    assertEquals(expectedName, grpFolder.getName());

    // now let's change the group name, check folder name is updated (RSPAC-316)
    g1.setDisplayName("newdisplay");
    g1 = grpMgr.saveGroup(g1, admin);
    Folder labFolder = folderDao.getSharedFolderForGroup(g1);
    assertTrue(labFolder.getName().contains("newdisplay"));
    String updatedName = new DefaultGroupNamingStrategy().getSharedGroupName(g1);
    assertEquals(updatedName, labFolder.getName());
    assertFalse(updatedName.equals(expectedName));
  }

  @Test
  public void testSetUpSharedSnippetGroupFolderForGroupWithMembers()
      throws IllegalAddChildOperation {
    User admin = createAndSaveUserIfNotExists(CoreTestUtils.getRandomName(10));
    User user = createAndSaveUserIfNotExists(CoreTestUtils.getRandomName(10));
    Group g1 = new Group(CoreTestUtils.getRandomName(10), admin);
    g1 = grpMgr.saveGroup(g1, admin);
    initialiseContentWithEmptyContent(user, admin);

    g1 =
        grpMgr.addMembersToGroup(
            g1.getId(), Arrays.asList(new User[] {user, admin}), "", "admin2", admin);
    grpMgr.createSharedCommunalGroupFolders(g1.getId(), admin.getUsername());
    String expectedName = new DefaultGroupNamingStrategy().getSharedGroupSnippetName(g1);
    assertAllGroupMembersHaveSharedSnippetFolder(g1, expectedName);
  }

  @Test
  public void testAMemberAddedToGroupHasSharedSnippetFolderAdded() throws IllegalAddChildOperation {
    User admin = createAndSaveUserIfNotExists(CoreTestUtils.getRandomName(10));
    User user = createAndSaveUserIfNotExists(CoreTestUtils.getRandomName(10));
    Group g1 = new Group(CoreTestUtils.getRandomName(10), admin);
    g1 = grpMgr.saveGroup(g1, admin);
    initialiseContentWithEmptyContent(user, admin);

    g1 =
        grpMgr.addMembersToGroup(
            g1.getId(), Arrays.asList(new User[] {admin}), "", "admin2", admin);
    grpMgr.createSharedCommunalGroupFolders(g1.getId(), admin.getUsername());
    grpMgr.addUserToGroup(user.getUsername(), g1.getId(), RoleInGroup.DEFAULT);
    String expectedName = new DefaultGroupNamingStrategy().getSharedGroupSnippetName(g1);
    assertAllGroupMembersHaveSharedSnippetFolder(g1, expectedName);
  }

  @Test
  public void testAMemberRemoveFromGroupHasSharedSnippetFolderRemoved()
      throws IllegalAddChildOperation {
    User admin = createAndSaveUserIfNotExists(CoreTestUtils.getRandomName(10));
    User user = createAndSaveUserIfNotExists(CoreTestUtils.getRandomName(10));
    Group g1 = new Group(CoreTestUtils.getRandomName(10), admin);
    g1 = grpMgr.saveGroup(g1, admin);
    initialiseContentWithEmptyContent(user, admin);

    g1 =
        grpMgr.addMembersToGroup(
            g1.getId(), Arrays.asList(new User[] {admin}), "", "admin2", admin);
    grpMgr.createSharedCommunalGroupFolders(g1.getId(), admin.getUsername());
    grpMgr.addUserToGroup(user.getUsername(), g1.getId(), RoleInGroup.DEFAULT);
    String expectedName = new DefaultGroupNamingStrategy().getSharedGroupSnippetName(g1);
    assertAllGroupMembersHaveSharedSnippetFolder(g1, expectedName);
    logoutAndLoginAsSysAdmin();
    User sysadmin = userMgr.getUserByUsername(SYS_ADMIN_UNAME);
    grpMgr.removeUserFromGroup(user.getUsername(), g1.getId(), sysadmin);
    makeSharedSnippetFolderAssertionsForUser(user, expectedName, false);
  }

  private void assertAllGroupMembersHaveSharedSnippetFolder(Group g1, String expectedName) {
    for (User aUser : g1.getMembers()) {
      makeSharedSnippetFolderAssertionsForUser(aUser, expectedName, true);
    }
  }

  private void makeSharedSnippetFolderAssertionsForUser(
      User aUser, String expectedName, boolean shouldFolderExist) {
    Folder snippetFolder = recordMgr.getGallerySubFolderForUser(Folder.SNIPPETS_FOLDER, aUser);
    Folder snippetSharedFolder =
        snippetFolder.getSubFolderByName(SHARED_SNIPPETS_FOLDER_PREFIX + Folder.SHARED_FOLDER_NAME);
    Folder snippetLabGroups =
        snippetSharedFolder.getSubFolderByName(
            SHARED_SNIPPETS_FOLDER_PREFIX + Folder.LAB_GROUPS_FOLDER_NAME);
    Folder sharedSnippetsForGroup = snippetLabGroups.getSubFolderByName(expectedName);
    if (shouldFolderExist) {
      assertNotNull(sharedSnippetsForGroup);
    } else {
      assertNull(sharedSnippetsForGroup);
    }
  }

  @Test
  public void testMemberAcquiresPermissionsForRoleAdmin() throws IllegalAddChildOperation {

    User admin = createAndSaveRandomUser();
    User user = createAndSaveRandomUser();
    Group grp1 = new Group("g1", admin);
    grpMgr.saveGroup(grp1, admin);
    grp1.setAdmins("admin2"); // simulate from ui
    initialiseContentWithEmptyContent(admin, user);
    ;
    grpMgr.addMembersToGroup(
        grp1.getId(), Arrays.asList(new User[] {user, admin}), "", "admin2", admin);

    admin = userDao.get(admin.getId());

    Record toTest = TestFactory.createAnySD();
    toTest.setOwner(user);
    // check cant read/export all records
    RecordPermissionAdapter rpa = new RecordPermissionAdapter(toTest);
    rpa.setAction(PermissionType.READ);
    assertFalse(admin.isPermitted(rpa, true));
    rpa.setAction(PermissionType.EXPORT);
    assertFalse(admin.isPermitted(rpa, true));

    RSForm form = TestFactory.createAnyForm("temp1");
    form.setOwner(user);
    // check can read/export all tempaltes

    assertTrue(permissionUtils.isPermitted(form, PermissionType.READ, admin));
    assertTrue(permissionUtils.isPermitted(form, PermissionType.WRITE, admin));
    // assertTrue(admin.isPermitted(tpa, true));
    // tpa.setActions(PermissionType.READ);
    // assertTrue(admin.isPermitted(tpa, true));
  }

  // RSPAC-625
  @Test
  public void labGroupPICannotViewOtherPisHomeFolderInGroup() {
    User labGroupPI = createAndSaveAPi();
    User otherPI = createAndSaveAPi();
    initialiseContentWithEmptyContent(labGroupPI, otherPI);
    logoutAndLoginAs(labGroupPI);
    Group grp = createGroup(getRandomAlphabeticString("grp"), labGroupPI);
    grpMgr.addMembersToGroup(
        grp.getId(),
        Arrays.asList(new User[] {labGroupPI, otherPI}),
        labGroupPI.getUsername(),
        "",
        labGroupPI);

    // reload users to get permiss
    assertFalse(
        permissionUtils.isPermitted(otherPI.getRootFolder(), PermissionType.READ, labGroupPI));
  }

  // RSPAC-333 & RSPAC-1088
  @Test
  public void testLabAdminViewAll() throws Exception {

    // setup group with 4 members and some docs
    final User labGroupPI = createAndSaveAPi();
    final User labAdmin = createAndSaveRandomUser();
    final User user = createAndSaveRandomUser();
    final User other = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(labGroupPI, labAdmin, user, other);
    final Group grp = createGroup(getRandomAlphabeticString("grp"), labGroupPI);
    grpMgr.addMembersToGroup(
        grp.getId(),
        Arrays.asList(new User[] {labGroupPI, labAdmin, user, other}),
        labGroupPI.getUsername(),
        labAdmin.getUsername(),
        labGroupPI);
    grpMgr.createSharedCommunalGroupFolders(grp.getId(), labGroupPI.getUsername());

    logoutAndLoginAs(user);
    StructuredDocument userSdoc = createBasicDocumentInRootFolderWithText(user, "any");

    logoutAndLoginAs(other);
    StructuredDocument otherSdoc = createBasicDocumentInRootFolderWithText(other, "any");

    // non-PIs can't authorise lab admin to view all
    assertUserCantAuthoriseLabAdmin(labAdmin, user, grp);
    logoutAndLoginAs(labAdmin);
    assertLabAdminCantAuthoriseLabAdmin(labAdmin, grp);
    // labadmin can't view other users unshared docs
    assertAdminRead(labAdmin, false, userSdoc);
    // getViewableUsers include only themselve
    assertEquals(1, userDao.getViewableUsersByRole(labAdmin).size());
    // there is only a group folder in their Shared -> Lab Groups
    assertEquals(1, folderDao.getLabGroupFolderForUser(labAdmin).getChildrens().size());

    // pi should be able to authorize lab admin to view all
    logoutAndLoginAs(labGroupPI);
    StructuredDocument piSdoc = createBasicDocumentInRootFolderWithText(labGroupPI, "any");
    User authorisedAdmin =
        grpMgr.authorizeLabAdminToViewAll(labAdmin.getId(), labGroupPI, grp.getId(), true);
    // now log back in as admin, can now see userdoc but not pi doc
    assertAdminRead(authorisedAdmin, true, userSdoc, otherSdoc);
    assertAdminRead(authorisedAdmin, false, piSdoc);
    // also getViewableUsers includes these 2 users + himself
    assertEquals(3, userDao.getViewableUsersByRole(labAdmin).size());
    // and they can see both user folders in their Shared -> Lab Groups
    assertEquals(3, folderDao.getLabGroupFolderForUser(labAdmin).getChildrens().size());

    // now a new user will be added to group
    final User newuser = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(newuser);
    Folder newuserRootFolder = folderDao.getRootRecordForUser(newuser);
    logoutAndLoginAs(newuser);
    StructuredDocument newuserdoc = createBasicDocumentInRootFolderWithText(newuser, "any");
    grpMgr.addUserToGroup(newuser.getUsername(), grp.getId(), RoleInGroup.DEFAULT);

    // check that admin can see newuser's docs
    logoutAndLoginAs(authorisedAdmin);
    assertAdminRead(authorisedAdmin, true, userSdoc, otherSdoc);
    assertAdminRead(authorisedAdmin, true, newuserdoc);
    assertAdminRead(authorisedAdmin, true, newuserRootFolder);
    assertEquals(4, userDao.getViewableUsersByRole(labAdmin).size());
    assertEquals(4, folderDao.getLabGroupFolderForUser(labAdmin).getChildrens().size());

    // now remove newuser from group, permission will be revoked
    logoutAndLoginAs(labGroupPI);
    grpMgr.removeUserFromGroup(newuser.getUsername(), grp.getId(), labGroupPI);
    assertAdminRead(authorisedAdmin, false, newuserdoc);
    assertAdminRead(authorisedAdmin, false, newuserRootFolder);
    assertAdminRead(authorisedAdmin, true, userSdoc, otherSdoc);
    assertEquals(3, userDao.getViewableUsersByRole(labAdmin).size());
    assertEquals(3, folderDao.getLabGroupFolderForUser(labAdmin).getChildrens().size());

    // remove one of the original users 'other'
    logoutAndLoginAs(labGroupPI);
    grpMgr.removeUserFromGroup(other.getUsername(), grp.getId(), labGroupPI);
    // 'user' should still be visible
    assertAdminRead(authorisedAdmin, false, otherSdoc);
    assertAdminRead(authorisedAdmin, true, userSdoc);
    assertEquals(2, userDao.getViewableUsersByRole(labAdmin).size());
    assertEquals(2, folderDao.getLabGroupFolderForUser(labAdmin).getChildrens().size());

    // add 'other' back so there are 2 users + admin + Pi again
    logoutAndLoginAs(labGroupPI);
    grpMgr.addUserToGroup(other.getUsername(), grp.getId(), RoleInGroup.DEFAULT);
    assertEquals(3, userDao.getViewableUsersByRole(labAdmin).size());
    assertEquals(3, folderDao.getLabGroupFolderForUser(labAdmin).getChildrens().size());

    // now demote lab admin back to member role
    grpMgr.setRoleForUser(grp.getId(), labAdmin.getId(), RoleInGroup.DEFAULT.name(), labGroupPI);
    assertAdminRead(authorisedAdmin, false, userSdoc, otherSdoc);
    assertEquals(1, userDao.getViewableUsersByRole(labAdmin).size());
    assertEquals(1, folderDao.getLabGroupFolderForUser(labAdmin).getChildrens().size());

    // and promote back to lab admin...
    logoutAndLoginAs(labGroupPI);
    grpMgr.setRoleForUser(
        grp.getId(), labAdmin.getId(), RoleInGroup.RS_LAB_ADMIN.name(), labGroupPI);
    assertEquals(1, userDao.getViewableUsersByRole(labAdmin).size());
    assertEquals(1, folderDao.getLabGroupFolderForUser(labAdmin).getChildrens().size());

    // and promote back to lab admin with 'view all'
    authorisedAdmin =
        grpMgr.authorizeLabAdminToViewAll(labAdmin.getId(), labGroupPI, grp.getId(), true);
    assertEquals(3, userDao.getViewableUsersByRole(labAdmin).size());
    assertEquals(3, folderDao.getLabGroupFolderForUser(labAdmin).getChildrens().size());

    // now add 'newuser' user to the group againusers again...
    grpMgr.addUserToGroup(newuser.getUsername(), grp.getId(), RoleInGroup.DEFAULT);
    assertAdminRead(authorisedAdmin, true, newuserdoc, otherSdoc);
    assertEquals(4, userDao.getViewableUsersByRole(labAdmin).size());
    assertEquals(4, folderDao.getLabGroupFolderForUser(labAdmin).getChildrens().size());

    // now revoke read all permission:
    logoutAndLoginAs(labGroupPI);
    authorisedAdmin =
        grpMgr.authorizeLabAdminToViewAll(labAdmin.getId(), labGroupPI, grp.getId(), false);
    assertAdminRead(authorisedAdmin, false, newuserdoc, otherSdoc);
    assertEquals(1, userDao.getViewableUsersByRole(labAdmin).size());
    assertEquals(1, folderDao.getLabGroupFolderForUser(labAdmin).getChildrens().size());
    // and restore again
    logoutAndLoginAs(labGroupPI);
    authorisedAdmin =
        grpMgr.authorizeLabAdminToViewAll(labAdmin.getId(), labGroupPI, grp.getId(), true);
    assertAdminRead(authorisedAdmin, true, newuserdoc, otherSdoc);
    assertEquals(4, folderDao.getLabGroupFolderForUser(labAdmin).getChildrens().size());

    // lad admin can't view all in a collaboration group.
    final User otherGrpPi = createAndSaveAPi();
    initialiseContentWithEmptyContent(otherGrpPi);
    Group otherGroup = createGroup(getRandomAlphabeticString("other"), otherGrpPi);
    addUsersToGroup(otherGrpPi, otherGroup);
    logoutAndLoginAs(otherGrpPi);
    final Group collabGrp = createCollabGroupBetweenGroups(otherGroup, grp);
    grpMgr.addUserToGroup(labAdmin.getUsername(), grp.getId(), RoleInGroup.RS_LAB_ADMIN);
    assertExceptionThrown(
        () ->
            grpMgr.authorizeLabAdminToViewAll(
                labAdmin.getId(), otherGrpPi, collabGrp.getId(), true),
        UnsupportedOperationException.class);

    // and delete the labgroup - perms should be removed
    User sysadmin = logoutAndLoginAsSysAdmin();
    grpMgr.removeGroup(grp.getId(), sysadmin);

    assertAdminRead(authorisedAdmin, false, newuserdoc, otherSdoc);
    assertEquals(1, userDao.getViewableUsersByRole(labAdmin).size());
  }

  // RSPAC-1113
  @Test
  public void groupAdminCanSeeUserFolderAfterRemovingUserFromOneOfTwoGroups() {

    /* set up pi, lab admin and member */
    User pi = createAndSaveUserIfNotExists("pi", Constants.PI_ROLE);
    User labAdmin = createAndSaveUserIfNotExists("labAdmin");
    User member = createAndSaveUserIfNotExists("member");
    initialiseContentWithEmptyContent(pi, labAdmin, member);

    /* set up two groups */
    Group group = new Group("unique1", pi);
    group = grpMgr.saveGroup(group, pi);
    addUsersToGroup(pi, group, labAdmin, member);
    logoutAndLoginAs(pi);
    grpMgr.setRoleForUser(group.getId(), labAdmin.getId(), RoleInGroup.RS_LAB_ADMIN.name(), pi);
    logoutAndLoginAs(pi);
    grpMgr.authorizeLabAdminToViewAll(labAdmin.getId(), pi, group.getId(), true);

    Group group2 = new Group("unique2", pi);
    group2 = grpMgr.saveGroup(group2, pi);
    addUsersToGroup(pi, group2, labAdmin, member);
    logoutAndLoginAs(pi);
    grpMgr.setRoleForUser(group2.getId(), labAdmin.getId(), RoleInGroup.RS_LAB_ADMIN.name(), pi);

    grpMgr.authorizeLabAdminToViewAll(labAdmin.getId(), pi, group2.getId(), true);

    /* pi and lab admin can see both groups and member's folder */
    assertEquals(4, folderDao.getLabGroupFolderForUser(pi).getChildren().size());
    assertEquals(3, folderDao.getLabGroupFolderForUser(labAdmin).getChildren().size());
    /* member can see both groups */
    assertEquals(2, folderDao.getLabGroupFolderForUser(member).getChildren().size());

    /* remove user from one of the groups */
    grpMgr.removeUserFromGroup(member.getUsername(), group.getId(), pi);
    /* pi and lab admin can still see member's folder */
    assertEquals(4, folderDao.getLabGroupFolderForUser(pi).getChildren().size());
    assertEquals(3, folderDao.getLabGroupFolderForUser(labAdmin).getChildren().size());
    /* member can see only one group */
    assertEquals(1, folderDao.getLabGroupFolderForUser(member).getChildren().size());

    /* remove user from second group */
    grpMgr.removeUserFromGroup(member.getUsername(), group2.getId(), pi);
    /* pi and lab admin can no longer see member's folder */
    assertEquals(3, folderDao.getLabGroupFolderForUser(pi).getChildren().size());
    assertEquals(2, folderDao.getLabGroupFolderForUser(labAdmin).getChildren().size());
    /* member can't see any group folder */
    assertEquals(0, folderDao.getLabGroupFolderForUser(member).getChildren().size());
  }

  @Test
  public void testPiCanEditAll() {
    // Let's enable this functionality
    User sysadmin = logoutAndLoginAsSysAdmin();
    systemPropertyManager.save(
        Preference.PI_CAN_EDIT_ALL_WORK_IN_LABGROUP.name(),
        HierarchicalPermission.ALLOWED.name(),
        sysadmin);

    // Setup group with 4 members and some docs
    User labGroupPI = createAndSaveAPi();
    final User labAdmin = createAndSaveRandomUser();
    final User user = createAndSaveRandomUser();
    final User other = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(labGroupPI, labAdmin, user, other);
    Group grp = createGroup(getRandomAlphabeticString("grp"), labGroupPI);
    grpMgr.addMembersToGroup(
        grp.getId(),
        Arrays.asList(new User[] {labGroupPI, labAdmin, user, other}),
        labGroupPI.getUsername(),
        labAdmin.getUsername(),
        labGroupPI);
    grpMgr.createSharedCommunalGroupFolders(grp.getId(), labGroupPI.getUsername());

    logoutAndLoginAs(user);
    StructuredDocument userSdoc = createBasicDocumentInRootFolderWithText(user, "any");

    logoutAndLoginAs(other);
    StructuredDocument otherSdoc = createBasicDocumentInRootFolderWithText(other, "any");

    // Verify that PI cannot edit the documents
    assertAdminRead(labGroupPI, true, userSdoc, otherSdoc);
    assertUserCanEdit(labGroupPI, false, userSdoc, otherSdoc);

    // Enable PI can edit all for this PI
    grpMgr.authorizePIToEditAll(grp.getId(), labGroupPI, true);

    // Verify that PI can edit the documents
    assertAdminRead(labGroupPI, true, userSdoc, otherSdoc);
    assertUserCanEdit(labGroupPI, true, userSdoc, otherSdoc);

    // Disable PI can edit all for this PI
    grpMgr.authorizePIToEditAll(grp.getId(), labGroupPI, false);

    // Verify that PI cannot edit the documents
    assertAdminRead(labGroupPI, true, userSdoc, otherSdoc);
    assertUserCanEdit(labGroupPI, false, userSdoc, otherSdoc);
  }

  @Test
  public void testPiCanEditAllRevokeBySysadmin() {
    // Let's enable this functionality
    User sysadmin = logoutAndLoginAsSysAdmin();
    systemPropertyManager.save(
        Preference.PI_CAN_EDIT_ALL_WORK_IN_LABGROUP.name(),
        HierarchicalPermission.ALLOWED.name(),
        sysadmin);

    // Setup group with 4 members and some docs
    final User labGroupPI = createAndSaveAPi();
    final User labAdmin = createAndSaveRandomUser();
    final User user = createAndSaveRandomUser();
    final User other = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(labGroupPI, labAdmin, user, other);
    final Group grp = createGroup(getRandomAlphabeticString("grp"), labGroupPI);
    grpMgr.addMembersToGroup(
        grp.getId(),
        toList(labGroupPI, labAdmin, user, other),
        labGroupPI.getUsername(),
        labAdmin.getUsername(),
        labGroupPI);
    grpMgr.createSharedCommunalGroupFolders(grp.getId(), labGroupPI.getUsername());

    logoutAndLoginAs(user);
    StructuredDocument userSdoc = createBasicDocumentInRootFolderWithText(user, "any");

    logoutAndLoginAs(other);
    StructuredDocument otherSdoc = createBasicDocumentInRootFolderWithText(other, "any");

    // Enable PI can edit all for this PI
    grpMgr.authorizePIToEditAll(grp.getId(), labGroupPI, true);

    // Verify that PI can edit the documents
    assertAdminRead(labGroupPI, true, userSdoc, otherSdoc);
    assertUserCanEdit(labGroupPI, true, userSdoc, otherSdoc);

    // Let's disable this functionality
    logoutAndLoginAsSysAdmin();
    systemPropertyManager.save(
        Preference.PI_CAN_EDIT_ALL_WORK_IN_LABGROUP.name(),
        HierarchicalPermission.DENIED.name(),
        sysadmin);

    // Verify that PI cannot edit the documents
    assertAdminRead(labGroupPI, true, userSdoc, otherSdoc);
    assertUserCanEdit(labGroupPI, false, userSdoc, otherSdoc);
  }

  @Test
  public void testPiCanEditAllRevokeByCommunityAdmin() {
    // Let's enable this functionality
    User sysadmin = logoutAndLoginAsSysAdmin();
    systemPropertyManager.save(
        Preference.PI_CAN_EDIT_ALL_WORK_IN_LABGROUP.name(),
        HierarchicalPermission.ALLOWED.name(),
        sysadmin);

    // Setup group with 4 members and some docs
    final User labGroupPI = createAndSaveAPi();
    final User labAdmin = createAndSaveRandomUser();
    final User user = createAndSaveRandomUser();
    final User other = createAndSaveRandomUser();
    final User admin = createAndSaveAdminUser();
    initialiseContentWithEmptyContent(labGroupPI, labAdmin, user, other);
    final Group grp = createGroup(getRandomAlphabeticString("grp"), labGroupPI);
    grpMgr.addMembersToGroup(
        grp.getId(),
        Arrays.asList(new User[] {labGroupPI, labAdmin, user, other}),
        labGroupPI.getUsername(),
        labAdmin.getUsername(),
        labGroupPI);
    grpMgr.createSharedCommunalGroupFolders(grp.getId(), labGroupPI.getUsername());

    logoutAndLoginAs(admin);
    Community community = createAndSaveCommunity(admin, getRandomAlphabeticString("community"));
    community = communityMgr.addGroupToCommunity(grp.getId(), community.getId(), admin);
    saveSystemPropertyValue(
        Preference.PI_CAN_EDIT_ALL_WORK_IN_LABGROUP.name(),
        HierarchicalPermission.ALLOWED,
        community,
        admin);

    logoutAndLoginAs(user);
    StructuredDocument userSdoc = createBasicDocumentInRootFolderWithText(user, "any");

    logoutAndLoginAs(other);
    StructuredDocument otherSdoc = createBasicDocumentInRootFolderWithText(other, "any");

    // Enable PI can edit all for this PI
    grpMgr.authorizePIToEditAll(grp.getId(), labGroupPI, true);

    // Verify that PI can edit the documents
    assertAdminRead(labGroupPI, true, userSdoc, otherSdoc);
    assertUserCanEdit(labGroupPI, true, userSdoc, otherSdoc);

    // Let's disable this functionality on community level
    logoutAndLoginAs(admin);
    saveSystemPropertyValue(
        Preference.PI_CAN_EDIT_ALL_WORK_IN_LABGROUP.name(),
        HierarchicalPermission.DENIED,
        community,
        admin);

    // Verify that PI cannot edit the documents
    assertAdminRead(labGroupPI, true, userSdoc, otherSdoc);
    assertUserCanEdit(labGroupPI, false, userSdoc, otherSdoc);
  }

  private void assertAdminRead(User authorisedAdmin, boolean canRead, BaseRecord... docs) {
    authorisedAdmin = userDao.get(authorisedAdmin.getId()); // refresh perms
    logoutAndLoginAs(authorisedAdmin);
    permissionUtils.refreshCache();

    // no docs should be visible
    for (BaseRecord doc : docs) {
      assertEquals(canRead, permissionUtils.isPermitted(doc, PermissionType.READ, authorisedAdmin));
    }
  }

  private void assertUserCanEdit(User user, boolean canWrite, BaseRecord... docs) {
    permissionUtils.refreshCache();
    user = userDao.get(user.getId()); // refresh perms
    logoutAndLoginAs(user);
    permissionUtils.refreshCache();

    for (BaseRecord doc : docs) {
      assertEquals(canWrite, permissionUtils.isPermitted(doc, PermissionType.WRITE, user));
    }
  }

  private void assertLabAdminCantAuthoriseLabAdmin(final User labadmin, final Group grp)
      throws Exception {
    assertAuthorisationExceptionThrown(
        () -> grpMgr.authorizeLabAdminToViewAll(labadmin.getId(), labadmin, grp.getId(), true));
  }

  private void assertUserCantAuthoriseLabAdmin(
      final User labadmin, final User user, final Group grp) throws Exception {
    assertAuthorisationExceptionThrown(
        () -> grpMgr.authorizeLabAdminToViewAll(labadmin.getId(), user, grp.getId(), true));
  }

  private void saveSystemPropertyValue(
      String property, HierarchicalPermission permission, Community community, User admin) {
    SystemPropertyValue systemPropertyValue =
        systemPropertyManager.findByNameAndCommunity(property, community.getId());

    if (systemPropertyValue == null) {
      SystemProperty systemProperty = systemPropertyManager.findByName(property).getProperty();
      systemPropertyValue =
          new SystemPropertyValue(systemProperty, permission.toString(), community);
    } else {
      systemPropertyValue.setValue(permission.toString());
    }
    systemPropertyManager.save(systemPropertyValue, admin);
  }

  @Test
  public void testSetNewPiValidation() throws Exception {
    TestGroup testgroup = createTestGroup(2);
    Group grp = testgroup.getGroup();
    User toMakePi = testgroup.getUserByPrefix("u1");
    User sysadmin = logoutAndLoginAsSysAdmin();

    assertExceptionThrown(
        () -> grpMgr.setNewPi(grp.getId(), toMakePi.getId(), sysadmin),
        IllegalArgumentException.class);

    // must be in group
    User piOutWithGroup = createAndSaveAPi();
    assertExceptionThrown(
        () -> grpMgr.setNewPi(grp.getId(), piOutWithGroup.getId(), sysadmin),
        IllegalArgumentException.class);

    // new PI can't be the current PI user
    assertExceptionThrown(
        () -> grpMgr.setNewPi(grp.getId(), toMakePi.getId(), sysadmin),
        IllegalArgumentException.class);
    // collab group rejected. this use case is only for LabGroups
    TestGroup tg2 = createTestGroup(1);
    logoutAndLoginAs(tg2.getPi());
    Group collabGroup = createCollabGroupBetweenGroups(tg2.getGroup(), grp);
    logoutAndLoginAsSysAdmin();
    assertExceptionThrown(
        () -> grpMgr.setNewPi(collabGroup.getId(), tg2.getUserByPrefix("u1").getId(), sysadmin),
        IllegalArgumentException.class);
    // TODO new pi has group permissions; old pi has lost them
    // should oonly be able to set RIG pi if user has global PI role
  }
}
