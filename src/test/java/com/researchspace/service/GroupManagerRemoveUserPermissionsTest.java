package com.researchspace.service;

import static com.researchspace.testutils.TestGroup.LABADMIN_PREFIX;
import static org.junit.Assert.assertEquals;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.testutils.TestGroup;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GroupManagerRemoveUserPermissionsTest extends GroupPermissionsTestBase {

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void labAdminRemoveUserFromGroupPermissions() throws Exception {
    TestGroup testgrp = createTestGroup(1, new TestGroupConfig(true));
    User admin = testgrp.getUserByPrefix(LABADMIN_PREFIX);
    logoutAndLoginAs(admin);
    // u2 can now remmove u3 from group
    // TODO this works in application; in this test the permissions aren't reloaded propley and
    // AuthEx thrown
    removeUser(testgrp, "u1", admin);
    assertGroupMemberCount(2, testgrp.getGroup()); // pi + labadmin
    // lab admin can't remove PI
    assertAuthorisationExceptionThrown(() -> removeUser(testgrp, "pi", admin));
    // lab admin cannot remove labadmin as does not have permission to alter role remove himself
    assertAuthorisationExceptionThrown(() -> removeUser(testgrp, LABADMIN_PREFIX, admin));
    assertGroupMemberCount(2, testgrp.getGroup()); // pi + labadmin
  }

  @Test
  public void userInGroupRemoveUserFromGroupPermissions() throws Exception {
    TestGroup testgrp = createTestGroup(1, new TestGroupConfig(true));
    User u1 = testgrp.getUserByPrefix("u1");
    final int initialGrpSize = 3;
    logoutAndLoginAs(u1);
    assertNoPermissionsToRemoveGrpMembers(testgrp, u1, initialGrpSize);
  }

  @Test
  public void piInGroupRemoveUserFromGroupPermissions() throws Exception {
    TestGroup testgrp = createTestGroup(1, new TestGroupConfig(true));
    User otherPi = addOtherPiToGroup(testgrp);
    testgrp.addUser(otherPi);
    final int initialGrpSize = 4;
    assertGroupMemberCount(initialGrpSize, testgrp.getGroup());
    User pi = testgrp.getPi();
    logoutAndLoginAs(pi);
    assertPermissionsToRemoveAllUsersInGroup(testgrp, otherPi, pi, initialGrpSize);
  }

  @Test
  public void piOutsideGroupRemoveUserFromGroupPermissions() throws Exception {
    TestGroup testgrp = createTestGroup(1, new TestGroupConfig(true));
    User otherPi = addOtherPiToGroup(testgrp);
    testgrp.addUser(otherPi);
    final int initialGrpSize = 4;
    assertGroupMemberCount(initialGrpSize, testgrp.getGroup());
    TestGroup otherGroup = createTestGroup(0);
    User piOutOfGroup = otherGroup.getPi();
    logoutAndLoginAs(piOutOfGroup);
    assertNoPermissionsToRemoveGrpMembers(testgrp, piOutOfGroup, initialGrpSize);
  }

  @Test
  public void commAdminInCommunityRemoveUserFromGroupPermissions() throws Exception {
    TestGroup testgrp = createTestGroup(1, new TestGroupConfig(true));
    User otherPi = addOtherPiToGroup(testgrp);
    testgrp.addUser(otherPi);
    User commAdmin = createCommunity(testgrp);
    final int initialGrpSize = 4;
    assertGroupMemberCount(initialGrpSize, testgrp.getGroup());
    logoutAndLoginAs(commAdmin);
    // commAdmin can remove anyone in group
    assertPermissionsToRemoveAllUsersInGroup(testgrp, otherPi, commAdmin, initialGrpSize);
  }

  @Test
  public void commAdminOutsideCommunityRemoveUserFromGroupPermissions() throws Exception {
    TestGroup testgrp = createTestGroup(1, new TestGroupConfig(true));
    User otherPi = addOtherPiToGroup(testgrp);
    testgrp.addUser(otherPi);
    TestGroup otherGroup = createTestGroup(1);
    User commAdminOfDifferentCommunity = createCommunity(otherGroup);
    final int initialGrpSize = 4;
    assertGroupMemberCount(initialGrpSize, testgrp.getGroup());
    logoutAndLoginAs(commAdminOfDifferentCommunity);
    assertNoPermissionsToRemoveGrpMembers(testgrp, commAdminOfDifferentCommunity, initialGrpSize);
  }

  private void assertNoPermissionsToRemoveGrpMembers(
      TestGroup testgrp, User commAdminOfDifferentCommunity, final int initialGrpSize)
      throws Exception {
    assertAuthorisationExceptionThrown(
        () -> removeUser(testgrp, "pi", commAdminOfDifferentCommunity));
    assertAuthorisationExceptionThrown(
        () -> removeUser(testgrp, LABADMIN_PREFIX, commAdminOfDifferentCommunity));
    assertAuthorisationExceptionThrown(
        () -> removeUser(testgrp, "u1", commAdminOfDifferentCommunity));
    assertGroupMemberCount(initialGrpSize, testgrp.getGroup());
  }

  @Test
  public void sysadminRemoveUserFromGroupPermissions() throws Exception {
    TestGroup testgrp = createTestGroup(1, new TestGroupConfig(true));
    User otherPi = addOtherPiToGroup(testgrp);
    testgrp.addUser(otherPi);
    final int initialGrpSize = 4;
    assertGroupMemberCount(initialGrpSize, testgrp.getGroup());
    User sysadmin = logoutAndLoginAsSysAdmin();
    // sysadmin can remove anyone in group
    assertPermissionsToRemoveAllUsersInGroup(testgrp, otherPi, sysadmin, initialGrpSize);
  }

  private void assertPermissionsToRemoveAllUsersInGroup(
      TestGroup testgrp, User otherPi, User commAdmin, final int initialGrpSize) {
    removeUser(testgrp, LABADMIN_PREFIX, commAdmin);
    assertGroupMemberCount(initialGrpSize - 1, testgrp.getGroup());
    removeUser(testgrp, "u1", commAdmin);
    assertGroupMemberCount(initialGrpSize - 2, testgrp.getGroup());
    removeUser(testgrp, otherPi.getUsername(), commAdmin);
    assertGroupMemberCount(initialGrpSize - 3, testgrp.getGroup());
  }

  private void assertGroupMemberCount(final int expectedGrpMemberCount, Group grp) {
    assertEquals(expectedGrpMemberCount, grpMgr.getGroup(grp.getId()).getMembers().size());
  }

  private void removeUser(TestGroup testgrp, String userPrefix, User subject) {
    grpMgr.removeUserFromGroup(
        testgrp.getUserByPrefix(userPrefix).getUsername(), testgrp.getGroup().getId(), subject);
  }
}
