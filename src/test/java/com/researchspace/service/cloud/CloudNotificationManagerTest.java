package com.researchspace.service.cloud;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.cloud.impl.CloudNotificationManagerImpl;
import com.researchspace.service.impl.ConfigurableLogger;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.controller.SignupController;
import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

public class CloudNotificationManagerTest extends SpringTransactionalTest {

  @Autowired private CloudNotificationManager cloudNotificationMgr;

  private AnalyticsManager analyticsMgrMock;

  private MockHttpServletRequest request;
  // private StringAppenderForTestLogging testLogger;

  private User inviterPI;
  private Group inviterGroup;
  private User invitee;
  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Mock Logger log;

  @After
  public void teardown() throws Exception {
    getBeanOfClass(ConfigurableLogger.class).setLoggerDefault();
  }

  @Before
  public void setUp() throws Exception {

    analyticsMgrMock = mock(AnalyticsManager.class);
    ((CloudNotificationManagerImpl) cloudNotificationMgr).setAnalyticsManager(analyticsMgrMock);

    request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.5");

    getBeanOfClass(ConfigurableLogger.class).setLogger(log);

    inviterPI = TestFactory.createAnyUser("inviter");
    inviterPI.addRole(Role.PI_ROLE);
    inviterGroup = TestFactory.createAnyGroup(inviterPI, inviterPI);
    invitee = createAndSaveUserIfNotExists("invitee");
  }

  // @Test
  public void testSendJoinGroupRequest() {
    // Should we use RealTransactionSpringTestBase instead ??
  }

  // @Test
  public void testSendCreateGroupRequest() {
    // Should we use RealTransactionSpringTestBase instead ??
  }

  @Test
  public void testSendInvitationEmailToTempUser() throws MessagingException {
    invitee.setTempAccount(true);
    cloudNotificationMgr.sendJoinGroupInvitationEmail(inviterPI, invitee, inviterGroup, request);

    verify(log, times(1))
        .info(
            Mockito.anyString(),
            Mockito.anyBoolean(),
            Mockito.contains("Join Group"),
            Mockito.contains(invitee.getEmail()),
            Mockito.contains("/signup?token="));

    // when inviting temporary user analytics should be informed
    verify(analyticsMgrMock, times(1)).joinGroupInvitationSent(inviterPI, invitee, request);
  }

  @Test
  public void testSendInvitationEmailToExistingUser() throws MessagingException {

    cloudNotificationMgr.sendJoinGroupInvitationEmail(inviterPI, invitee, inviterGroup, request);

    // if is not a temp user, they get link to workspace
    //	assertTrue(testLogger.logContents.contains("/login"));
    verify(log, times(1))
        .info(
            Mockito.anyString(),
            Mockito.anyBoolean(),
            Mockito.contains("Join Group"),
            Mockito.contains(invitee.getEmail()),
            Mockito.contains("/login"));
    verify(log, never()).info(Mockito.contains("/signup?token="));
    verify(log, never()).info(Mockito.matches(".*\\$.*"));
    // assertFalse(testLogger.logContents.contains("/signup?token="));
    // assertVelocityVariablesReplaced(testLogger.logContents);

    // for existing non-temporary user we shouldn't record analytics invitation event
    verify(analyticsMgrMock, never())
        .joinGroupInvitationSent(
            Matchers.any(User.class),
            Matchers.any(User.class),
            Matchers.any(HttpServletRequest.class));
  }

  @Test
  public void testSendPIInvitationEmailToTempUser() throws MessagingException {
    invitee.setTempAccount(true);
    cloudNotificationMgr.sendPIInvitationEmail(inviterPI, invitee, "GroupName", request);

    verify(log, times(1))
        .info(
            Mockito.anyString(),
            Mockito.anyBoolean(),
            Mockito.contains("Create Group"),
            Mockito.contains(invitee.getEmail()),
            Mockito.contains(SignupController.SIGNUP_URL));

    // when inviting temporary user analytics should be informed
    verify(analyticsMgrMock, times(1)).joinGroupInvitationSent(inviterPI, invitee, request);
  }

  @Test
  public void testSendPIInvitationEmailToExistingUser() throws MessagingException {
    cloudNotificationMgr.sendPIInvitationEmail(inviterPI, invitee, "GroupName", request);

    verify(log, times(1))
        .info(
            Mockito.anyString(),
            Mockito.anyBoolean(),
            Mockito.contains("Create Group"),
            Mockito.contains(invitee.getEmail()),
            Mockito.contains("/login"));

    // for existing non-temporary user we shouldn't record analytics invitation event
    verify(analyticsMgrMock, never())
        .joinGroupInvitationSent(
            Matchers.any(User.class),
            Matchers.any(User.class),
            Matchers.any(HttpServletRequest.class));
  }

  @Test
  public void testSendShareRecordEmailToTempUser() throws MessagingException {

    invitee.setTempAccount(true);
    cloudNotificationMgr.sendShareRecordInvitationEmail(inviterPI, invitee, "recordName", request);

    // when sharing with temporary user analytics should be informed
    verify(analyticsMgrMock, times(1)).shareDocInvitationSent(inviterPI, invitee, request);
  }

  @Test
  public void testSendShareRecordEmailToExistingUser() throws MessagingException {

    cloudNotificationMgr.sendShareRecordInvitationEmail(inviterPI, invitee, "recordName", request);

    // for existing non-temporary user we shouldn't record analytics invitation event
    verify(analyticsMgrMock, never())
        .shareDocInvitationSent(
            Matchers.any(User.class),
            Matchers.any(User.class),
            Matchers.any(HttpServletRequest.class));
  }
}
