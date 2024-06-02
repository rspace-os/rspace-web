package com.researchspace.service;

import static com.researchspace.testutils.TestGroup.LABADMIN_PREFIX;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.testutils.TestGroup;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GroupManagerChangeUserRolePermissionsTest extends GroupPermissionsTestBase {

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
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
    assertAuthorisationExceptionThrown(
        () -> assertSetLabAdminViewAllAuthorised(testgrp, otherCommAdmin));
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
    assertAuthorisationExceptionThrown(
        () -> assertSetLabAdminViewAllAuthorised(testgrp, other.getPi()));
  }

  @Test
  public void labAdminInGroupChangeUserRoleInGroupPermissions() throws Exception {
    TestGroup testgrp = createTestGroup(1, new TestGroupConfig(true));
    logoutAndLoginAs(getLabAdmin(testgrp));
    assertAlterRoleNotAuthorised(testgrp, getLabAdmin(testgrp));
    assertAuthorisationExceptionThrown(
        () -> assertSetLabAdminViewAllAuthorised(testgrp, getLabAdmin(testgrp)));
  }

  @Test
  public void userInGroupChangeUserRoleInGroupPermissions() throws Exception {
    TestGroup testgrp = createTestGroup(1, new TestGroupConfig(true));
    User useringroup = getLabAdmin(testgrp);
    logoutAndLoginAs(useringroup);
    assertAlterRoleNotAuthorised(testgrp, useringroup);
    assertAuthorisationExceptionThrown(
        () -> assertSetLabAdminViewAllAuthorised(testgrp, useringroup));
  }

  private User getLabAdmin(TestGroup testgrp) {
    return testgrp.getUserByPrefix(LABADMIN_PREFIX);
  }

  private void assertAlterRoleNotAuthorised(TestGroup testgrp, User subject) throws Exception {
    assertAuthorisationExceptionThrown(
        () -> setAndAssertUserRole(testgrp, "u1", RoleInGroup.RS_LAB_ADMIN, subject));
    assertAuthorisationExceptionThrown(
        () -> setAndAssertUserRole(testgrp, "u1", RoleInGroup.DEFAULT, subject));
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

  private void assertSetLabAdminViewAllAuthorised(TestGroup testgrp, User subject) {
    User labAdmin = getLabAdmin(testgrp);
    grpMgr.authorizeLabAdminToViewAll(labAdmin.getId(), subject, testgrp.getGroup().getId(), true);
    grpMgr.authorizeLabAdminToViewAll(labAdmin.getId(), subject, testgrp.getGroup().getId(), false);
  }
}
