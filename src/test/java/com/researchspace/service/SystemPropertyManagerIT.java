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
    sysPropMgr.save(SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.ALLOWED, subject);

    // In case of a null user, isPropertyAllowed will check system admin level property only
    assertTrue(
        systemPropertyPermissionUtils.isPropertyAllowed(
            (User) null, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));

    // DENIED_BY_DEFAULT -> not available
    sysPropMgr.save(
        SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.DENIED_BY_DEFAULT, subject);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            (User) null, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));

    // DENIED -> not available
    sysPropMgr.save(SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.DENIED, subject);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            (User) null, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
  }

  @Test
  public void enablingSystemPropertyUserWithNoLabGroups() {
    // This user does not belong to any lab groups, so only system admin level properties apply to
    // it
    User user = createAndSaveUser(getRandomAlphabeticString("anyUser"));
    subject = logoutAndLoginAsSysAdmin();
    // Enable Ecat on system admin level
    sysPropMgr.save(SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.ALLOWED, subject);
    assertTrue(
        systemPropertyPermissionUtils.isPropertyAllowed(
            user, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
    // DENIED_BY_DEFAULT -> not available
    sysPropMgr.save(
        SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.DENIED_BY_DEFAULT, subject);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            user, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
    // DENIED -> not available
    sysPropMgr.save(SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.DENIED, subject);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            user, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
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
    sysPropMgr.save(SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.ALLOWED, subject);
    assertTrue(
        systemPropertyPermissionUtils.isPropertyAllowed(
            user, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
    // System wide denied by default
    sysPropMgr.save(
        SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.DENIED_BY_DEFAULT, subject);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            user, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
    // System wide denied
    sysPropMgr.save(SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.DENIED, subject);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            user, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));

    // Community wide settings

    // System wide allowed
    sysPropMgr.save(SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.ALLOWED, subject);

    // Community wide allowed
    saveSystemPropertyValue(
        SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.ALLOWED, community, subject);
    assertTrue(
        systemPropertyPermissionUtils.isPropertyAllowed(
            user, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
    // Community wide denied by default
    saveSystemPropertyValue(
        SystemPropertyName.BOX_AVAILABLE,
        HierarchicalPermission.DENIED_BY_DEFAULT,
        community,
        sysadmin);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            user, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
    // Community wide denied
    saveSystemPropertyValue(
        SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.DENIED, community, sysadmin);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            user, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));

    // System wide denied by default
    sysPropMgr.save(
        SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.DENIED_BY_DEFAULT, sysadmin);

    // Community wide allowed
    saveSystemPropertyValue(
        SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.ALLOWED, community, sysadmin);
    assertTrue(
        systemPropertyPermissionUtils.isPropertyAllowed(
            user, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
    // Community wide denied by default
    saveSystemPropertyValue(
        SystemPropertyName.BOX_AVAILABLE,
        HierarchicalPermission.DENIED_BY_DEFAULT,
        community,
        sysadmin);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            user, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
    // Community wide denied
    saveSystemPropertyValue(
        SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.DENIED, community, sysadmin);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            user, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));

    // System wide denied
    sysPropMgr.save(SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.DENIED, sysadmin);

    // Community wide allowed
    saveSystemPropertyValue(
        SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.ALLOWED, community, sysadmin);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            user, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
    // Community wide denied by default
    saveSystemPropertyValue(
        SystemPropertyName.BOX_AVAILABLE,
        HierarchicalPermission.DENIED_BY_DEFAULT,
        community,
        sysadmin);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            user, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
    // Community wide denied
    saveSystemPropertyValue(
        SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.DENIED, community, sysadmin);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            user, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
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
    sysPropMgr.save(SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.ALLOWED, sysadmin);
    assertTrue(
        systemPropertyPermissionUtils.isPropertyAllowed(
            group, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
    // System wide denied by default
    sysPropMgr.save(
        SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.DENIED_BY_DEFAULT, sysadmin);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            group, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
    // System wide denied
    sysPropMgr.save(SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.DENIED, sysadmin);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            group, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));

    // Community wide settings

    // System wide allowed
    sysPropMgr.save(SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.ALLOWED, sysadmin);

    // Community wide allowed
    saveSystemPropertyValue(
        SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.ALLOWED, community, sysadmin);
    assertTrue(
        systemPropertyPermissionUtils.isPropertyAllowed(
            group, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
    // Community wide denied by default
    saveSystemPropertyValue(
        SystemPropertyName.BOX_AVAILABLE,
        HierarchicalPermission.DENIED_BY_DEFAULT,
        community,
        sysadmin);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            group, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
    // Community wide denied
    saveSystemPropertyValue(
        SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.DENIED, community, sysadmin);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            group, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));

    // System wide denied by default
    sysPropMgr.save(
        SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.DENIED_BY_DEFAULT, sysadmin);

    // Community wide allowed
    saveSystemPropertyValue(
        SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.ALLOWED, community, sysadmin);
    assertTrue(
        systemPropertyPermissionUtils.isPropertyAllowed(
            group, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
    // Community wide denied by default
    saveSystemPropertyValue(
        SystemPropertyName.BOX_AVAILABLE,
        HierarchicalPermission.DENIED_BY_DEFAULT,
        community,
        sysadmin);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            group, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
    // Community wide denied
    saveSystemPropertyValue(
        SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.DENIED, community, sysadmin);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            group, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));

    // System wide denied
    sysPropMgr.save(SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.DENIED, sysadmin);

    // Community wide allowed
    saveSystemPropertyValue(
        SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.ALLOWED, community, sysadmin);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            group, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
    // Community wide denied by default
    saveSystemPropertyValue(
        SystemPropertyName.BOX_AVAILABLE,
        HierarchicalPermission.DENIED_BY_DEFAULT,
        community,
        sysadmin);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            group, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
    // Community wide denied
    saveSystemPropertyValue(
        SystemPropertyName.BOX_AVAILABLE, HierarchicalPermission.DENIED, community, sysadmin);
    assertFalse(
        systemPropertyPermissionUtils.isPropertyAllowed(
            group, SystemPropertyName.BOX_AVAILABLE.getPropertyName()));
  }

  private void saveSystemPropertyValue(
      SystemPropertyName name,
      HierarchicalPermission permission,
      Community community,
      User subject2) {
    SystemPropertyValue systemPropertyValue =
        sysPropMgr.findByNameAndCommunity(name.getPropertyName(), community.getId());

    if (systemPropertyValue == null) {
      SystemProperty systemProperty = sysPropMgr.findByName(name).getProperty();
      systemPropertyValue =
          new SystemPropertyValue(systemProperty, permission.toString(), community);
    } else {
      systemPropertyValue.setValue(permission.toString());
    }

    sysPropMgr.save(systemPropertyValue, subject2);
  }
}
