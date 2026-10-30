package com.researchspace.service;

import static com.researchspace.testutils.TestGroup.LABADMIN_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.Group;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.testutils.TestGroup;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GroupManagerChangeUserRolePermissionsTest extends GroupPermissionsTestBase {

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void sysadminChangeUserRoleInGroupPermissions() throws Exception {
    TestGroup testgrp = createTestGroup(1, new TestGroupConfig(true));
    User sysadmin = logoutAndLoginAsSysAdmin();
    assertRolesCanBeAltered(testgrp, sysadmin);
    setAndAssertUserRole(testgrp, LABADMIN_PREFIX, RoleInGroup.RS_LAB_ADMIN, sysadmin);
    assertSetLabAdminViewAllAuthorised(testgrp, sysadmin);
  }

  @Test
  public void commAdminInCommunityChangeUserRoleInGroupPermissions() throws Exception {
    TestGroup testgrp = createTestGroup(1, new TestGroupConfig(true));
    User commAdmin = createCommunity(testgrp);
    logoutAndLoginAs(commAdmin);
    assertRolesCanBeAltered(testgrp, commAdmin);
    setAndAssertUserRole(testgrp, LABADMIN_PREFIX, RoleInGroup.RS_LAB_ADMIN, commAdmin);
    assertSetLabAdminViewAllAuthorised(testgrp, commAdmin);
  }

  @Test
  public void commAdminOutsideCommunityChangeUserRoleInGroupPermissions() throws Exception {
    TestGroup testgrp = createTestGroup(1, new TestGroupConfig(true));
    TestGroup otherGroup = createTestGroup(0);
    User otherCommAdmin = createCommunity(otherGroup);
    logoutAndLoginAs(otherCommAdmin);
    assertAlterRoleNotAuthorised(testgrp, otherCommAdmin);
    assertSetLabAdminViewAllNotAuthorised(testgrp, otherCommAdmin);
  }

  @Test
  public void PiInGroupChangeUserRoleInGroupPermissions() throws Exception {
    TestGroup testgrp = createTestGroup(1, new TestGroupConfig(true));
    logoutAndLoginAs(testgrp.getPi());
    assertRolesCanBeAltered(testgrp, testgrp.getPi());
    setAndAssertUserRole(testgrp, LABADMIN_PREFIX, RoleInGroup.RS_LAB_ADMIN, testgrp.getPi());
    assertSetLabAdminViewAllAuthorised(testgrp, testgrp.getPi());
  }

  @Test
  public void PiOutOfGroupChangeUserRoleInGroupPermissions() throws Exception {
    TestGroup testgrp = createTestGroup(1, new TestGroupConfig(true));
    TestGroup other = createTestGroup(0);
    logoutAndLoginAs(other.getPi());
    assertAlterRoleNotAuthorised(testgrp, other.getPi());
    assertSetLabAdminViewAllNotAuthorised(testgrp, other.getPi());
  }

  @Test
  public void labAdminInGroupChangeUserRoleInGroupPermissions() throws Exception {
    TestGroup testgrp = createTestGroup(1, new TestGroupConfig(true));
    logoutAndLoginAs(getLabAdmin(testgrp));
    assertAlterRoleNotAuthorised(testgrp, getLabAdmin(testgrp));
    assertSetLabAdminViewAllNotAuthorised(testgrp, getLabAdmin(testgrp));
  }

  @Test
  public void userInGroupChangeUserRoleInGroupPermissions() throws Exception {
    TestGroup testgrp = createTestGroup(1, new TestGroupConfig(true));
    User useringroup = getLabAdmin(testgrp);
    logoutAndLoginAs(useringroup);
    assertAlterRoleNotAuthorised(testgrp, useringroup);
    assertSetLabAdminViewAllNotAuthorised(testgrp, useringroup);
  }

  @Test
  public void memberInCollabGroupCannotChangeRoles() throws Exception {
    TestGroup tg1 = createTestGroup(1);
    TestGroup tg2 = createTestGroup(1);
    Group collab = createCollabGroupWithLabMembers(tg1, tg2);
    User member = tg1.u1();
    logoutAndLoginAs(member);

    assertCollabRoleChangeNotAuthorised(collab, member, member, RoleInGroup.RS_LAB_ADMIN);
    assertCollabRoleChangeNotAuthorised(collab, member, tg2.u1(), RoleInGroup.RS_LAB_ADMIN);
    assertCollabRoleChangeNotAuthorised(collab, member, tg2.getPi(), RoleInGroup.DEFAULT);
    assertCollabRole(collab, member, RoleInGroup.DEFAULT);
    assertCollabRole(collab, tg2.u1(), RoleInGroup.DEFAULT);
    assertCollabRole(collab, tg2.getPi(), RoleInGroup.PI);
  }

  @Test
  public void piInCollabGroupCanChangeMemberRoles() throws Exception {
    TestGroup tg1 = createTestGroup(1);
    TestGroup tg2 = createTestGroup(1);
    Group collab = createCollabGroupWithLabMembers(tg1, tg2);
    logoutAndLoginAs(tg1.getPi());
    assertCollabRolesCanBeAltered(collab, tg2.u1(), tg1.getPi());
  }

  @Test
  public void sysadminCanChangeCollabGroupRoles() throws Exception {
    TestGroup tg1 = createTestGroup(1);
    TestGroup tg2 = createTestGroup(1);
    Group collab = createCollabGroupWithLabMembers(tg1, tg2);
    User sysadmin = logoutAndLoginAsSysAdmin();
    assertCollabRolesCanBeAltered(collab, tg1.u1(), sysadmin);
  }

  @Test
  public void labAdminInCollabGroupCanRemoveAnotherLabAdmin() throws Exception {
    TestGroup tg1 = createTestGroup(1);
    TestGroup tg2 = createTestGroup(1);
    Group collab = createCollabGroupWithLabMembers(tg1, tg2);
    User labAdmin = tg1.u1();
    User otherLabAdmin = tg2.u1();
    for (User u : new User[] {labAdmin, otherLabAdmin}) {
      grpMgr.setRoleForUser(
          collab.getId(), u.getId(), RoleInGroup.RS_LAB_ADMIN.name(), tg1.getPi());
    }

    logoutAndLoginAs(labAdmin);
    grpMgr.removeUserFromGroup(otherLabAdmin.getUsername(), collab.getId(), labAdmin);

    assertFalse(grpMgr.getGroup(collab.getId()).hasMember(otherLabAdmin));
  }

  private Group createCollabGroupWithLabMembers(TestGroup tg1, TestGroup tg2) {
    logoutAndLoginAs(tg1.getPi());
    Group collab = createCollabGroupBetweenGroups(tg1.getGroup(), tg2.getGroup());
    grpMgr.addUserToGroup(tg1.u1().getUsername(), collab.getId(), RoleInGroup.DEFAULT);
    grpMgr.addUserToGroup(tg2.u1().getUsername(), collab.getId(), RoleInGroup.DEFAULT);
    return collab;
  }

  private void assertCollabRoleChangeNotAuthorised(
      Group collab, User subject, User toChange, RoleInGroup role) {
    Long groupId = collab.getId();
    Long userId = toChange.getId();
    assertThrows(
        AuthorizationException.class,
        () -> grpMgr.setRoleForUser(groupId, userId, role.name(), subject));
  }

  private void assertCollabRolesCanBeAltered(Group collab, User toChange, User subject) {
    grpMgr.setRoleForUser(
        collab.getId(), toChange.getId(), RoleInGroup.RS_LAB_ADMIN.name(), subject);
    assertCollabRole(collab, toChange, RoleInGroup.RS_LAB_ADMIN);
    grpMgr.setRoleForUser(collab.getId(), toChange.getId(), RoleInGroup.DEFAULT.name(), subject);
    assertCollabRole(collab, toChange, RoleInGroup.DEFAULT);
  }

  private void assertCollabRole(Group collab, User user, RoleInGroup expected) {
    assertEquals(
        expected, grpMgr.getGroup(collab.getId()).getUserGroupForUser(user).getRoleInGroup());
  }

  private User getLabAdmin(TestGroup testgrp) {
    return testgrp.getUserByPrefix(LABADMIN_PREFIX);
  }

  private void assertAlterRoleNotAuthorised(TestGroup testgrp, User subject) throws Exception {
    Long groupId = testgrp.getGroup().getId();
    Long userId = testgrp.getUserByPrefix("u1").getId();
    String labAdminRole = RoleInGroup.RS_LAB_ADMIN.name();
    String defaultRole = RoleInGroup.DEFAULT.name();
    assertThrows(
        AuthorizationException.class,
        () -> grpMgr.setRoleForUser(groupId, userId, labAdminRole, subject));
    assertThrows(
        AuthorizationException.class,
        () -> grpMgr.setRoleForUser(groupId, userId, defaultRole, subject));
  }

  private void assertRolesCanBeAltered(TestGroup testgrp, User subject) {
    setAndAssertUserRole(testgrp, "u1", RoleInGroup.RS_LAB_ADMIN, subject);
    setAndAssertUserRole(testgrp, "u1", RoleInGroup.DEFAULT, subject);
    setAndAssertUserRole(testgrp, LABADMIN_PREFIX, RoleInGroup.DEFAULT, subject);
  }

  private void setAndAssertUserRole(
      TestGroup testgrp, String prefix, RoleInGroup groupRole, User subject) {
    grpMgr.setRoleForUser(
        testgrp.getGroup().getId(),
        testgrp.getUserByPrefix(prefix).getId(),
        groupRole.name(),
        subject);
    assertTrue(testgrp.getUserByPrefix(prefix).hasRoleInGroup(testgrp.getGroup(), groupRole));
  }

  private void assertSetLabAdminViewAllNotAuthorised(TestGroup testgrp, User subject)
      throws Exception {
    Long labAdminId = getLabAdmin(testgrp).getId();
    Long groupId = testgrp.getGroup().getId();
    assertThrows(
        AuthorizationException.class,
        () -> grpMgr.authorizeLabAdminToViewAll(labAdminId, subject, groupId, true));
    assertThrows(
        AuthorizationException.class,
        () -> grpMgr.authorizeLabAdminToViewAll(labAdminId, subject, groupId, false));
  }

  private void assertSetLabAdminViewAllAuthorised(TestGroup testgrp, User subject) {
    User labAdmin = getLabAdmin(testgrp);
    grpMgr.authorizeLabAdminToViewAll(labAdmin.getId(), subject, testgrp.getGroup().getId(), true);
    grpMgr.authorizeLabAdminToViewAll(labAdmin.getId(), subject, testgrp.getGroup().getId(), false);
  }
}
