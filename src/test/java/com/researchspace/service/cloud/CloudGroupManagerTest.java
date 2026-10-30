package com.researchspace.service.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.Group;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IGroupPermissionUtils;
import com.researchspace.properties.IMutablePropertyHolder;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestGroup;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// RA Feb 19
// Ideally we would annotate with @CommunityTest. But in this case
// something strange happens - on Jenkins, the user is not saved in the DB and authentication fails.
// no idea why this happens. Must be something to do with spring context reloading.
public class CloudGroupManagerTest extends SpringTransactionalTest {

  private @Autowired IGroupPermissionUtils grpPermUtils;
  private @Autowired IMutablePropertyHolder propertyHolder;
  private @Autowired CloudGroupManager cloudGroupManager;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @AfterEach
  public void after() throws Exception {
    super.setUp();
    propertyHolder.setCloud("false"); // revert to default setting
  }

  @Test
  public void userCanSelfDeleteOnCommunity_RSPAC1662() throws Exception {
    TestGroup tg = createTestGroup(1);
    User subject = tg.u1();
    logoutAndLoginAs(subject);
    String piUsername = tg.getPi().getUsername();
    var group = tg.getGroup();
    // non community fail
    assertThrows(
        AuthorizationException.class,
        () -> grpPermUtils.assertLeaveGroupPermissions(piUsername, subject, group));
    // now succeeds
    propertyHolder.setCloud("true");
    assertEquals(
        subject,
        grpPermUtils.assertLeaveGroupPermissions(subject.getUsername(), subject, tg.getGroup()));

    // sole  pi not able to remove himself
    assertThrows(
        AuthorizationException.class,
        () -> grpPermUtils.assertLeaveGroupPermissions(piUsername, subject, group));
  }

  @Test
  public void collabGroupLabAdminCanSelfRemoveOnCommunity() throws Exception {
    TestGroup tg1 = createTestGroup(1);
    TestGroup tg2 = createTestGroup(0);
    logoutAndLoginAs(tg1.getPi());
    Group collabGroup = createCollabGroupBetweenGroups(tg1.getGroup(), tg2.getGroup());
    User labAdmin = tg1.u1();
    grpMgr.addUserToGroup(labAdmin.getUsername(), collabGroup.getId(), RoleInGroup.DEFAULT);
    logoutAndLoginAs(tg1.getPi());
    grpMgr.setRoleForUser(
        collabGroup.getId(), labAdmin.getId(), RoleInGroup.RS_LAB_ADMIN.name(), tg1.getPi());

    propertyHolder.setCloud("true");
    logoutAndLoginAs(labAdmin);
    grpMgr.removeUserFromGroup(labAdmin.getUsername(), collabGroup.getId(), labAdmin);

    assertFalse(grpMgr.getGroup(collabGroup.getId()).hasMember(labAdmin));
  }

  @Test
  public void promotionToCollabGroupPiRefusedForAnotherNonAdminUser() throws Exception {
    TestGroup tg1 = createTestGroup(1);
    TestGroup tg2 = createTestGroup(1);
    logoutAndLoginAs(tg1.getPi());
    Group collabGroup = createCollabGroupBetweenGroups(tg1.getGroup(), tg2.getGroup());
    User member = tg1.u1();
    grpMgr.addUserToGroup(member.getUsername(), collabGroup.getId(), RoleInGroup.DEFAULT);

    User other = tg2.u1();
    logoutAndLoginAs(other);
    User toPromote = userMgr.get(member.getId());
    assertThrows(
        AuthorizationException.class, () -> cloudGroupManager.promoteUserToPI(toPromote, other));

    assertEquals(
        RoleInGroup.DEFAULT,
        grpMgr.getGroup(collabGroup.getId()).getUserGroupForUser(member).getRoleInGroup());
  }
}
