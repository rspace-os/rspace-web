package com.researchspace.webapp.controller;

import static com.researchspace.core.testutil.CoreTestUtils.assertIllegalStateExceptionThrown;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.impl.DevEmailSenderImpl;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.stereotype.Component;

public class UsernameReminderTest extends SpringTransactionalTest {
  private static final String U2_EMAIL = "u2@researchspace.com";
  // use this just for token bucket test
  private static final String U3_EMAIL = "user3@researchspace.com";
  @Autowired UsernameReminderByEmailHandlerTSS usernameReminderByEmailHandlerTSS;
  Logger logger;
  DevEmailSenderImpl emailSender;

  @Before
  public void before() {
    logger = Mockito.mock(Logger.class);
    emailSender = new DevEmailSenderImpl();
    emailSender.setLogger(logger);
  }

  @Component
  static class UsernameReminderByEmailHandlerTSS extends UsernameReminderByEmailHandler {
    public void setEmailer(EmailBroadcast emailer) {
      this.emailer = emailer;
    }

    public void setLogger(Logger logger) {
      this.SECURITY_LOG = logger;
    }
  }

  /**
   * Uses DevEmailSenderImpl as the email sender, which outputs what would be sent in the log. Log
   * testing is done in testLogging.
   */
  @Test
  public void testEmailSending() {

    usernameReminderByEmailHandlerTSS.setEmailer(emailSender);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");

    // An email shouldn't be sent to a non-existing email -> no new log events
    usernameReminderByEmailHandlerTSS.sendUsernameReminderEmail(
        request, "non-existing-email@email.com");
    Mockito.verifyZeroInteractions(logger);

    usernameReminderByEmailHandlerTSS.sendUsernameReminderEmail(request, U2_EMAIL);
    Mockito.verify(logger)
        .info(
            Mockito.anyString(),
            Mockito.eq(Boolean.TRUE),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString());
    Mockito.verify(logger)
        .info(
            Mockito.anyString(),
            Mockito.eq(Boolean.TRUE),
            Mockito.contains("username reminder"),
            Mockito.contains(U2_EMAIL),
            Mockito.anyString());
  }

  @Test
  public void tokenBucketLimitingOfEmails() {
    // int tooManyReminders = UsernameReminderByEmailHandler.MAX_REMINDERS_PER_EMAIL_PER_HOUR + 1;
    usernameReminderByEmailHandlerTSS.setEmailer(emailSender);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");
    IntStream.range(0, UsernameReminderByEmailHandler.MAX_REMINDERS_PER_EMAIL_PER_HOUR)
        .forEach(
            i -> usernameReminderByEmailHandlerTSS.sendUsernameReminderEmail(request, U3_EMAIL));

    assertIllegalStateExceptionThrown(
        () -> usernameReminderByEmailHandlerTSS.sendUsernameReminderEmail(request, U3_EMAIL));
  }

  @Test
  public void testLogging() {

    usernameReminderByEmailHandlerTSS.setLogger(logger);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");

    CoreTestUtils.assertIllegalArgumentException(
        () -> usernameReminderByEmailHandlerTSS.sendUsernameReminderEmail(request, ""));

    usernameReminderByEmailHandlerTSS.sendUsernameReminderEmail(
        request, "non-existing-email@email.com");
    Mockito.verify(logger)
        .warn(
            "Username reminder request for unknown email [{}], from {}",
            "non-existing-email@email.com",
            "127.0.0.1");

    usernameReminderByEmailHandlerTSS.sendUsernameReminderEmail(request, U2_EMAIL);
    Mockito.verify(logger)
        .info("Username reminder request from {} sent to [{}]", "127.0.0.1", U2_EMAIL);
  }
}
