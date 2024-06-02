package com.researchspace.service.impl;

import static com.researchspace.core.testutil.CoreTestUtils.assertExceptionThrown;
import static org.mockito.Mockito.when;

import com.researchspace.dao.CommunicationDao;
import com.researchspace.dao.GroupDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.MsgOrReqstCreationCfg;
import com.researchspace.model.comms.RequestFactory;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.TestFactory;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.IMessageAndNotificationTracker;
import com.researchspace.service.OperationFailedMessageGenerator;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class MessageOrRequestCreatorManagerImplTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  private @Mock CommunicationDao commDao;
  private @Mock IPermissionUtils permUtils;
  private @Mock GroupDao grpDao;
  private @Mock RecordDao recordDao;
  private @Mock UserDao userDao;
  private @Mock CommunicationManager commMgr;
  private @Mock IMessageAndNotificationTracker notificnTracker;
  private @Mock RequestFactory reqFactory;
  private @Mock OperationFailedMessageGenerator authMsgGenerator;
  private @Mock IPropertyHolder properties;
  @InjectMocks MessageOrRequestCreatorManagerImpl requestCreator;

  Group group;
  User pi, anyUser, notInGroup, toInvite;

  @Before
  public void before() {
    anyUser = TestFactory.createAnyUser("any");
    notInGroup = TestFactory.createAnyUser("notInGroup");
    toInvite = TestFactory.createAnyUser("toInvite");
    pi = TestFactory.createAnyUserWithRole("pi", com.researchspace.Constants.PI_ROLE);
    group = TestFactory.createAnyGroup(pi, anyUser);
    group.setId(1L);
  }

  // rspac-1999
  @Test
  public void groupMemberCanInviteOtherUsersOnCommunity() throws Exception {
    MsgOrReqstCreationCfg cmd = createInviteToLabGroupCmnd();
    setUpMocks(cmd, anyUser);
    when(properties.isCloud()).thenReturn(true);
    requestCreator.checkPerms(cmd, anyUser);

    // non-community not allowed
    when(properties.isCloud()).thenReturn(false);
    assertExceptionThrown(
        () -> requestCreator.checkPerms(cmd, anyUser), AuthorizationException.class);
  }

  @Test
  public void nonGroupMemberCannotInviteToGroupOnCommunity() throws Exception {
    MsgOrReqstCreationCfg cmd = createInviteToLabGroupCmnd();
    setUpMocks(cmd, notInGroup);
    when(properties.isCloud()).thenReturn(true);
    // never permitted
    assertExceptionThrown(
        () -> requestCreator.checkPerms(cmd, notInGroup), AuthorizationException.class);
    when(properties.isCloud()).thenReturn(false);
    assertExceptionThrown(
        () -> requestCreator.checkPerms(cmd, notInGroup), AuthorizationException.class);
  }

  private void setUpMocks(MsgOrReqstCreationCfg cmd, User subject) {
    when(userDao.getUserByUserName(subject.getUsername())).thenReturn(subject);
    when(grpDao.get(1L)).thenReturn(group);
    when(permUtils.isPermitted(cmd.getMessageType(), PermissionType.READ, subject))
        .thenReturn(true);
  }

  private MsgOrReqstCreationCfg createInviteToLabGroupCmnd() {
    MsgOrReqstCreationCfg cmd = new MsgOrReqstCreationCfg();
    cmd.setMessageType(MessageType.REQUEST_JOIN_LAB_GROUP);
    cmd.setGroupId(group.getId());
    cmd.setRecipientnames(toInvite.getUsername());
    return cmd;
  }
}
