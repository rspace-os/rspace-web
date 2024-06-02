package com.researchspace.service;

import static com.researchspace.testutils.TestGroup.LABADMIN_PREFIX;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.MsgOrReqstCreationCfg;
import com.researchspace.testutils.TestGroup;
import java.util.HashSet;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class GroupManagerEditMetadataPermissionsTest extends GroupPermissionsTestBase {

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  private @Autowired MessageOrRequestCreatorManager requestCreateMgr;

  @Test
  public void groupMetaDataEditPermissionsByRole() throws Exception {

    TestGroup testgrp = createTestGroup(1, new TestGroupConfig(true));
    User commAdmin = createCommunity(testgrp);
    User pi = testgrp.getPi();
    User labAdmin = testgrp.getUserByPrefix(LABADMIN_PREFIX);
    User grpMember = testgrp.getUserByPrefix("u1");
    User sysadmin = logoutAndLoginAsSysAdmin();
    TestGroup outsideGrp = createTestGroup(0);
    User commAdminOutside = createCommunity(outsideGrp);
    User outsidePi = outsideGrp.getPi();
    List<User> expectedAuthorisedGroupEditors =
        TransformerUtils.toList(sysadmin, commAdmin, pi, labAdmin);
    List<User> expectedUnauthorisedGroupEditors =
        TransformerUtils.toList(grpMember, outsidePi, commAdminOutside);

    final Group grp = testgrp.getGroup();
    for (User subject : expectedAuthorisedGroupEditors) {

      assertRenameAuthorized(grp, subject, true);
      User newUserToInvite = createAndSaveRandomUser();
      assertInviteNewUserAuthorised(subject, newUserToInvite, grp);
    }
    for (User subject : expectedUnauthorisedGroupEditors) {
      assertRenameAuthorized(grp, subject, false);
      User newUserToInvite = createAndSaveRandomUser();
      assertAuthorisationExceptionThrown(
          () -> assertInviteNewUserAuthorised(subject, newUserToInvite, grp));
    }
  }

  private void assertInviteNewUserAuthorised(User subject, User newUserToInvite, Group grp) {
    if (subject.hasRole(Role.SYSTEM_ROLE)) {
      logoutAndLoginAsSysAdmin();
    } else {
      logoutAndLoginAs(subject);
    }
    MsgOrReqstCreationCfg cgf = new MsgOrReqstCreationCfg(subject, permissionUtils);
    cgf.setGroupId(grp.getId());
    cgf.setMessageType(MessageType.REQUEST_JOIN_LAB_GROUP);
    grp.setMemberString(TransformerUtils.toList(newUserToInvite.getUsername()));
    MessageOrRequest request =
        requestCreateMgr.createRequest(
            cgf, subject.getUsername(), new HashSet<String>(grp.getMemberString()), null, null);
  }

  private Group assertRenameAuthorized(Group grp, User subject, boolean isAuthorisedExpected)
      throws Exception {
    if (subject.hasRole(Role.SYSTEM_ROLE)) {
      logoutAndLoginAsSysAdmin();
    } else {
      logoutAndLoginAs(subject);
    }
    String oldNAme = grp.getDisplayName();
    String newName = getRandomAlphabeticString("newname_" + subject.getUsername());
    grp.setDisplayName(newName);
    if (isAuthorisedExpected) {
      grp = assertIsAuthorized(grp, subject);
    } else {
      assertNotAuthorized(grp, subject);
    }
    return grp;
  }

  private Group assertIsAuthorized(Group saveGroup, User subject) {
    return grpMgr.saveGroup(saveGroup, false, subject);
  }

  private void assertNotAuthorized(Group saveGroup, User subject) throws Exception {
    assertAuthorisationExceptionThrown(() -> grpMgr.saveGroup(saveGroup, false, subject));
  }
}
