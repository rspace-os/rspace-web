package com.researchspace.analytics.service.impl;

import static org.junit.Assert.assertEquals;

import com.researchspace.admin.service.UserUsageInfo;
import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.auth.BaseLoginHelperImpl;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.testutil.StringAppenderForTestLogging;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;

/**
 * This class is sending test data to segment.io server, to 'Development' source.
 *
 * <p>When running the test, sometimes message doesn't get to segment - perhaps the test finishes
 * and destroys Segment queue before it has chance to send the payload to segment. Run through the
 * debugger or insert sleep statements if you want to examine the payload in Segment itself.
 *
 * <p>Segment client is asynchronous and in case of error it just writes to log file. So as a test
 * assertion this class is checking segmentIO log for errors.
 */
@TestPropertySource(properties = {"analytics.enabled=true"})
@RunWith(ConditionalTestRunner.class)
public class AnalyticsManagerRealConnectionTest extends SpringTransactionalTest {
  private @Autowired AnalyticsManager analyticsManager;

  private final RecordFactory recordFactory = new RecordFactory();
  private StringAppenderForTestLogging testStringAppender;
  private User testUser;
  private MockHttpServletRequest mockHttpRequest;

  @Before
  public void setUp() throws Exception {
    super.setUp();

    testUser = createAndSaveUserIfNotExists("realConnectionTestUser", "ROLE_PI");

    Logger analyticsLogger = LogManager.getLogger(SegmentAnalyticsLogAdapter.class);
    testStringAppender = CoreTestUtils.configureStringLogger(analyticsLogger);

    mockHttpRequest = new MockHttpServletRequest();
    MockHttpSession mockHttpSession = new MockHttpSession();
    mockHttpSession.setAttribute(BaseLoginHelperImpl.RECENT_SIGNUP_ATTR, true);
    mockHttpSession.setAttribute(SessionAttributeUtils.FIRST_LOGIN, true);
    mockHttpRequest.setSession(mockHttpSession);
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void userCreatedEventForwardedToAnalytics() throws IOException {
    analyticsManager.userCreated(testUser);
    flushAnalyticsClientAndWait();
    assertRealConnectionWorkedFine();
  }

  private void flushAnalyticsClientAndWait() {
    analyticsManager.flushAnalyticsClient();

    /* flush is asynchronous, so we have to wait a bit
     * for log messages to start appearing */
    try {
      Thread.sleep(500);
    } catch (InterruptedException ignored) {
    }
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void invitationSentEventForwardedToAnalytics() throws IOException {
    User invitee = new User();
    invitee.setEmail("invited@email.com");

    analyticsManager.joinGroupInvitationSent(testUser, invitee, mockHttpRequest);
    analyticsManager.shareDocInvitationSent(testUser, invitee, mockHttpRequest);
    flushAnalyticsClientAndWait();

    assertRealConnectionWorkedFine();
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void signupEventForwardedToAnalytics() throws IOException {
    analyticsManager.userSignedUp(testUser, true, mockHttpRequest);
    flushAnalyticsClientAndWait();

    assertRealConnectionWorkedFine();
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void loginEventForwardedToAnalytics() throws IOException {
    analyticsManager.userLoggedIn(testUser, mockHttpRequest);
    flushAnalyticsClientAndWait();

    assertRealConnectionWorkedFine();
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void logoutEventForwardedToAnalytics() throws IOException {
    analyticsManager.userLoggedOut(testUser, mockHttpRequest);
    flushAnalyticsClientAndWait();

    assertRealConnectionWorkedFine();
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void recordCreatedEventForwardedToAnalytics() throws IOException {
    Record testDoc = recordFactory.createAnyRecord(testUser, "doc");
    Folder testFolder = recordFactory.createFolder("folder", testUser);

    analyticsManager.recordCreated(new GenericEvent(testUser, testDoc, AuditAction.CREATE));
    analyticsManager.recordCreated(new GenericEvent(testUser, testFolder, AuditAction.CREATE));
    flushAnalyticsClientAndWait();

    assertRealConnectionWorkedFine();
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void diskUsageEventsUploadedToAnalytics() throws IOException {
    UserUsageInfo userUsageInfo1 = new UserUsageInfo(testUser);
    userUsageInfo1.setFileUsage(2048L);

    analyticsManager.uploadUsersDiskUsage();
    flushAnalyticsClientAndWait();

    assertRealConnectionWorkedFine();
  }

  private void assertRealConnectionWorkedFine() {
    /* if you are investigating the test and want more details in logger, then comment out
     * the lines in SegmentAnalyticsLogAdapter to see VERBOSE level messages  */
    assertEquals(
        "no error should be printed into segmentIO logger", "", testStringAppender.logContents);
  }
}
