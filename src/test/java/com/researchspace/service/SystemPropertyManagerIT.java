package com.researchspace.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.Constants;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.model.system.SystemProperty;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

// Most of these tests are about enabling system features hierarchically described in RSPAC-1185
public class SystemPropertyManagerIT extends RealTransactionSpringTestBase {
  private static final String BOX_AVAILABLE = "box.available";

  private @Autowired SystemPropertyManager sysPropMgr;
  private @Autowired SystemPropertyPermissionManager systemPropertyPermissionUtils;
  private User subject;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void enablingSystemPropertyNullHandling() {
    subject = logoutAndLoginAsSysAdmin();
    // Enable Ecat on system admin level
    sysPropMgr.save(BOX_AVAILABLE, HierarchicalPermission.ALLOWED.toString(), subject);

    // In case of a null user, isPropertyAllowed will check system admin level property only
    assertTrue(systemPropertyPermissionUtils.isPropertyAllowed((User) null, BOX_AVAILABLE));

    // DENIED_BY_DEFAULT -> not available
    sysPropMgr.save(BOX_AVAILABLE, HierarchicalPermission.DENIED_BY_DEFAULT.toString(), subject);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed((User) null, BOX_AVAILABLE));

    // DENIED -> not available
    sysPropMgr.save(BOX_AVAILABLE, HierarchicalPermission.DENIED.toString(), subject);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed((User) null, BOX_AVAILABLE));
  }

  @Test
  public void enablingSystemPropertyUserWithNoLabGroups() {
    // This user does not belong to any lab groups, so only system admin level properties apply to
    // it
    User user = createAndSaveUser(getRandomAlphabeticString("anyUser"));
    subject = logoutAndLoginAsSysAdmin();
    // Enable Ecat on system admin level
    sysPropMgr.save(BOX_AVAILABLE, HierarchicalPermission.ALLOWED.toString(), subject);
    assertTrue(systemPropertyPermissionUtils.isPropertyAllowed(user, BOX_AVAILABLE));
    // DENIED_BY_DEFAULT -> not available
    sysPropMgr.save(BOX_AVAILABLE, HierarchicalPermission.DENIED_BY_DEFAULT.toString(), subject);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed(user, BOX_AVAILABLE));
    // DENIED -> not available
    sysPropMgr.save(BOX_AVAILABLE, HierarchicalPermission.DENIED.toString(), subject);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed(user, BOX_AVAILABLE));
  }

  @Test
  public void enablingSystemPropertyUserWithCommunity() {
    // This user belongs to a lab group, so both system admin level properties and community
    // settings apply to it
    User user = createAndSaveUser(getRandomAlphabeticString("user"), Constants.PI_ROLE);
    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    initUsers(user, admin);

    // Create a community and a lab group
    User sysadmin = logoutAndLoginAsSysAdmin();
    Community community = createAndSaveCommunity(admin, getRandomAlphabeticString("community"));
    Group group = createGroupForUsers(user, user.getUsername(), "", user);
    community = communityMgr.addGroupToCommunity(group.getId(), community.getId(), sysadmin);

    // No community wide setting

    // System wide allowed
    sysPropMgr.save(BOX_AVAILABLE, HierarchicalPermission.ALLOWED.toString(), subject);
    assertTrue(systemPropertyPermissionUtils.isPropertyAllowed(user, BOX_AVAILABLE));
    // System wide denied by default
    sysPropMgr.save(BOX_AVAILABLE, HierarchicalPermission.DENIED_BY_DEFAULT.toString(), subject);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed(user, BOX_AVAILABLE));
    // System wide denied
    sysPropMgr.save(BOX_AVAILABLE, HierarchicalPermission.DENIED.toString(), subject);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed(user, BOX_AVAILABLE));

    // Community wide settings

    // System wide allowed
    sysPropMgr.save(BOX_AVAILABLE, HierarchicalPermission.ALLOWED.toString(), subject);

    // Community wide allowed
    saveSystemPropertyValue(BOX_AVAILABLE, HierarchicalPermission.ALLOWED, community, subject);
    assertTrue(systemPropertyPermissionUtils.isPropertyAllowed(user, BOX_AVAILABLE));
    // Community wide denied by default
    saveSystemPropertyValue(
        BOX_AVAILABLE, HierarchicalPermission.DENIED_BY_DEFAULT, community, sysadmin);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed(user, BOX_AVAILABLE));
    // Community wide denied
    saveSystemPropertyValue(BOX_AVAILABLE, HierarchicalPermission.DENIED, community, sysadmin);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed(user, BOX_AVAILABLE));

    // System wide denied by default
    sysPropMgr.save(BOX_AVAILABLE, HierarchicalPermission.DENIED_BY_DEFAULT.toString(), sysadmin);

    // Community wide allowed
    saveSystemPropertyValue(BOX_AVAILABLE, HierarchicalPermission.ALLOWED, community, sysadmin);
    assertTrue(systemPropertyPermissionUtils.isPropertyAllowed(user, BOX_AVAILABLE));
    // Community wide denied by default
    saveSystemPropertyValue(
        BOX_AVAILABLE, HierarchicalPermission.DENIED_BY_DEFAULT, community, sysadmin);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed(user, BOX_AVAILABLE));
    // Community wide denied
    saveSystemPropertyValue(BOX_AVAILABLE, HierarchicalPermission.DENIED, community, sysadmin);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed(user, BOX_AVAILABLE));

    // System wide denied
    sysPropMgr.save(BOX_AVAILABLE, HierarchicalPermission.DENIED.toString(), sysadmin);

    // Community wide allowed
    saveSystemPropertyValue(BOX_AVAILABLE, HierarchicalPermission.ALLOWED, community, sysadmin);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed(user, BOX_AVAILABLE));
    // Community wide denied by default
    saveSystemPropertyValue(
        BOX_AVAILABLE, HierarchicalPermission.DENIED_BY_DEFAULT, community, sysadmin);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed(user, BOX_AVAILABLE));
    // Community wide denied
    saveSystemPropertyValue(BOX_AVAILABLE, HierarchicalPermission.DENIED, community, sysadmin);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed(user, BOX_AVAILABLE));
  }

  @Test
  public void enablingSystemPropertyGroupInCommunity() {
    // Initialize some users needed to create a lab group and a community
    User user = createAndSaveUser(getRandomAlphabeticString("user"), Constants.PI_ROLE);
    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    initUsers(user, admin);

    // Create a community and a lab group
    User sysadmin = logoutAndLoginAsSysAdmin();
    Community community = createAndSaveCommunity(admin, getRandomAlphabeticString("community"));
    Group group = createGroupForUsers(user, user.getUsername(), "", user);
    community = communityMgr.addGroupToCommunity(group.getId(), community.getId(), sysadmin);
    group = grpMgr.getGroupWithCommunities(group.getId());

    // No community wide setting

    // System wide allowed
    sysPropMgr.save(BOX_AVAILABLE, HierarchicalPermission.ALLOWED.toString(), sysadmin);
    assertTrue(systemPropertyPermissionUtils.isPropertyAllowed(group, BOX_AVAILABLE));
    // System wide denied by default
    sysPropMgr.save(BOX_AVAILABLE, HierarchicalPermission.DENIED_BY_DEFAULT.toString(), sysadmin);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed(group, BOX_AVAILABLE));
    // System wide denied
    sysPropMgr.save(BOX_AVAILABLE, HierarchicalPermission.DENIED.toString(), sysadmin);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed(group, BOX_AVAILABLE));

    // Community wide settings

    // System wide allowed
    sysPropMgr.save(BOX_AVAILABLE, HierarchicalPermission.ALLOWED.toString(), sysadmin);

    // Community wide allowed
    saveSystemPropertyValue(BOX_AVAILABLE, HierarchicalPermission.ALLOWED, community, sysadmin);
    assertTrue(systemPropertyPermissionUtils.isPropertyAllowed(group, BOX_AVAILABLE));
    // Community wide denied by default
    saveSystemPropertyValue(
        BOX_AVAILABLE, HierarchicalPermission.DENIED_BY_DEFAULT, community, sysadmin);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed(group, BOX_AVAILABLE));
    // Community wide denied
    saveSystemPropertyValue(BOX_AVAILABLE, HierarchicalPermission.DENIED, community, sysadmin);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed(group, BOX_AVAILABLE));

    // System wide denied by default
    sysPropMgr.save(BOX_AVAILABLE, HierarchicalPermission.DENIED_BY_DEFAULT.toString(), sysadmin);

    // Community wide allowed
    saveSystemPropertyValue(BOX_AVAILABLE, HierarchicalPermission.ALLOWED, community, sysadmin);
    assertTrue(systemPropertyPermissionUtils.isPropertyAllowed(group, BOX_AVAILABLE));
    // Community wide denied by default
    saveSystemPropertyValue(
        BOX_AVAILABLE, HierarchicalPermission.DENIED_BY_DEFAULT, community, sysadmin);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed(group, BOX_AVAILABLE));
    // Community wide denied
    saveSystemPropertyValue(BOX_AVAILABLE, HierarchicalPermission.DENIED, community, sysadmin);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed(group, BOX_AVAILABLE));

    // System wide denied
    sysPropMgr.save(BOX_AVAILABLE, HierarchicalPermission.DENIED.toString(), sysadmin);

    // Community wide allowed
    saveSystemPropertyValue(BOX_AVAILABLE, HierarchicalPermission.ALLOWED, community, sysadmin);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed(group, BOX_AVAILABLE));
    // Community wide denied by default
    saveSystemPropertyValue(
        BOX_AVAILABLE, HierarchicalPermission.DENIED_BY_DEFAULT, community, sysadmin);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed(group, BOX_AVAILABLE));
    // Community wide denied
    saveSystemPropertyValue(BOX_AVAILABLE, HierarchicalPermission.DENIED, community, sysadmin);
    assertFalse(systemPropertyPermissionUtils.isPropertyAllowed(group, BOX_AVAILABLE));
  }

  private void saveSystemPropertyValue(
      String property, HierarchicalPermission permission, Community community, User subject2) {
    SystemPropertyValue systemPropertyValue =
        sysPropMgr.findByNameAndCommunity(property, community.getId());

    if (systemPropertyValue == null) {
      SystemProperty systemProperty = sysPropMgr.findByName(property).getProperty();
      systemPropertyValue =
          new SystemPropertyValue(systemProperty, permission.toString(), community);
    } else {
      systemPropertyValue.setValue(permission.toString());
    }

    sysPropMgr.save(systemPropertyValue, subject2);
  }
}
