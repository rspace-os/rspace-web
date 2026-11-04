package com.researchspace.service;

import static com.researchspace.core.util.TransformerUtils.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.Group;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.MsgOrReqstCreationCfg;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class JoinExistingCollGroupRequestHandlerTest extends SpringTransactionalTest {

  @Autowired()
  @Qualifier("joinExistingCollabGroupRequestHandler")
  private RSpaceRequestUpdateHandler handler;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void testHandleRequest() {
    assertTrue(handler.handleRequest(MessageType.REQUEST_JOIN_EXISTING_COLLAB_GROUP));
  }

  @Test
  public void invitedPiJoinsCollabGroupAsPi() throws Exception {
    TestGroup tg1 = createTestGroup(0);
    TestGroup tg2 = createTestGroup(0);
    TestGroup tg3 = createTestGroup(0);
    logoutAndLoginAs(tg1.getPi());
    Group collabGroup = createCollabGroupBetweenGroups(tg1.getGroup(), tg2.getGroup());
    User invited = tg3.getPi();

    acceptInvitation(tg1.getPi(), invited, collabGroup);

    assertEquals(RoleInGroup.PI, grpMgr.getGroup(collabGroup.getId()).getRoleForUser(invited));
  }

  @Test
  public void piInvitedByACollabGroupLabAdminJoinsAsPi() throws Exception {
    TestGroup tg1 = createTestGroup(1);
    TestGroup tg2 = createTestGroup(0);
    TestGroup tg3 = createTestGroup(0);
    logoutAndLoginAs(tg1.getPi());
    Group collabGroup = createCollabGroupBetweenGroups(tg1.getGroup(), tg2.getGroup());
    User labAdmin = tg1.u1();
    grpMgr.addUserToGroup(labAdmin.getUsername(), collabGroup.getId(), RoleInGroup.RS_LAB_ADMIN);
    User invited = tg3.getPi();

    acceptInvitation(labAdmin, invited, collabGroup);

    assertEquals(RoleInGroup.PI, grpMgr.getGroup(collabGroup.getId()).getRoleForUser(invited));
  }

  @Test
  public void invitationFromADemotedPiAddsNobody() throws Exception {
    TestGroup tg1 = createTestGroup(0);
    TestGroup tg2 = createTestGroup(0);
    TestGroup tg3 = createTestGroup(0);
    logoutAndLoginAs(tg1.getPi());
    Group collabGroup = createCollabGroupBetweenGroups(tg1.getGroup(), tg2.getGroup());
    User inviter = tg1.getPi();
    User invited = tg3.getPi();
    MessageOrRequest invitation = sendInvitation(inviter, invited, collabGroup);

    logoutAndLoginAs(tg2.getPi());
    grpMgr.setRoleForUser(
        collabGroup.getId(), inviter.getId(), RoleInGroup.DEFAULT.name(), tg2.getPi());
    accept(invited, invitation);

    assertFalse(grpMgr.getGroup(collabGroup.getId()).hasMember(invited));
  }

  private void acceptInvitation(User inviter, User invited, Group collabGroup) {
    accept(invited, sendInvitation(inviter, invited, collabGroup));
  }

  private MessageOrRequest sendInvitation(User inviter, User invited, Group collabGroup) {
    logoutAndLoginAs(inviter);
    MsgOrReqstCreationCfg config = new MsgOrReqstCreationCfg();
    config.setMessageType(MessageType.REQUEST_JOIN_EXISTING_COLLAB_GROUP);
    config.setGroupId(collabGroup.getId());
    return reqCreateMgr.createRequest(
        config, inviter.getUsername(), toSet(invited.getUsername()), null, null);
  }

  private void accept(User invited, MessageOrRequest invitation) {
    logoutAndLoginAs(invited);
    reqUpdateMgr.updateStatus(
        invited.getUsername(), CommunicationStatus.COMPLETED, invitation.getId(), "ok");
  }
}
