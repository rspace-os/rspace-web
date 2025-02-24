package com.researchspace.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.testutil.StringAppenderForTestLogging;
import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.impl.EmailBroadcastImp.EmailContent;
import com.researchspace.testutils.SpringTransactionalTest;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.RetryConfig;
import java.lang.annotation.Annotation;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.mail.AuthenticationFailedException;
import javax.mail.IllegalWriteException;
import javax.mail.MessagingException;
import javax.mail.SendFailedException;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;

public class EmailBroadcastTest extends SpringTransactionalTest {

  private static final String BASEURL = "http://localhost:8080/";

  class AuthFailureEmailSenderTSS extends EmailBroadcastImp {
    @Override
    protected void sendMailToAddresses(EmailConfig config) throws MessagingException {
      throw new AuthenticationFailedException("test auth failure");
    }
  }

  class SendFailureEmailSenderTSS extends EmailBroadcastImp {
    @Override
    protected void sendMailToAddresses(EmailConfig config) throws MessagingException {
      throw new SendFailedException("test send failure");
    }
  }

  class GenericFailureEmailSenderTSS extends EmailBroadcastImp {
    @Override
    protected void sendMailToAddresses(EmailConfig config) throws MessagingException {
      throw new IllegalWriteException("test send failure");
    }
  }

  static class EmailSenderTSS extends EmailBroadcastImp {

    int messageCount = 0;

    @Override
    public void sendHtmlEmail(
        String subj, EmailContent content, List<String> recipients, Communication comm) {
      messageCount++;
    }

    public EmailContent generateEmailBody(Communication comm) {
      return EmailContent.builder().htmlContent("").build();
    }
  }

  private @Autowired StrictEmailContentGenerator strictEmailContentGenerator;

  private EmailBroadcastImp broadcast = new EmailBroadcastImp();
  EmailBroadcast api = broadcast;

  @Before
  public void setUp() throws Exception {
    // we need to explicitly create this, as it is only created by Spring in 'prod' profile
    // and these tests run in dev profile.
    broadcast = new EmailBroadcastImp();
    broadcast.setStrictEmailContentGenerator(strictEmailContentGenerator);
    broadcast.setHtmlDomainPrefix(BASEURL);
    broadcast.init();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testGenerateEmailForSimpleMessage() {
    User sender = createSender();
    MessageOrRequest mor = TestFactory.createAnyMessage(sender);
    mor.setMessage("message1");
    Record record = TestFactory.createAnyRecord(sender);
    record.setId(1L);
    mor.setRecord(record);
    EmailContent msgbody = broadcast.generateEmailBody(mor);

    assertAllVelocityVarsReplaced(msgbody.getHtmlContent());
    assertAllVelocityVarsReplaced(msgbody.getPlainTextContent().get());
  }

  private User createSender() {
    User sender = TestFactory.createAnyUser("sender");
    sender.setId(2L);
    return sender;
  }

  private void assertAllVelocityVarsReplaced(String msgbody) {
    assertFalse(msgbody.contains("$"));
  }

  @Test
  public void testGenerateEmailForRequest() {
    User sender = createSender();
    sender.addRole(Role.PI_ROLE);

    Record record = TestFactory.createAnyRecord(sender);
    Group group = TestFactory.createAnyGroup(sender);
    group.setId(999L);
    MessageOrRequest mor = TestFactory.createAnyGroupRequest(sender, record, group);
    mor.setMessage("message1");
    record.setId(1L);
    EmailContent msgbody = broadcast.generateEmailBody(mor);

    assertAllVelocityVarsReplaced(msgbody.getHtmlContent());
    assertTrue(
        msgbody.getHtmlContent().contains(BASEURL + "/workspace/editor/structuredDocument/1"));

    // now try with a notebook and assert the link is different:
    assertNotebookLinkGenerated(sender, mor);
  }

  private void assertNotebookLinkGenerated(User sender, Communication mor) {
    Notebook nb = TestFactory.createANotebook("any", sender);
    nb.setId(1L);
    mor.setRecord(nb);
    EmailContent msgbody = broadcast.generateEmailBody(mor);
    assertAllVelocityVarsReplaced(msgbody.getHtmlContent());
    assertTrue(msgbody.getHtmlContent().contains(BASEURL + "/notebookEditor/1"));
  }

  @Test
  public void testGenerateEmailForNotification() {
    User sender = createSender();

    Record record = TestFactory.createAnyRecord(sender);
    Notification not = TestFactory.createANotification(sender);

    not.setRecord(record);
    record.setId(1L);
    EmailContent msgbody = broadcast.generateEmailBody(not);
    assertEquals(2, StringUtils.countMatches(msgbody.getHtmlContent(), "href"));

    assertAllVelocityVarsReplaced(msgbody.getHtmlContent());
    // now try with a notebook and assert the link is different:
    assertNotebookLinkGenerated(sender, not);

    // now use a notification type that shouldn't include link to doc;
    not.setNotificationType(NotificationType.NOTIFICATION_DOCUMENT_DELETED);
    msgbody = broadcast.generateEmailBody(not);
    assertAllVelocityVarsReplaced(msgbody.getHtmlContent());
    // no record link
    assertEquals(1, StringUtils.countMatches(msgbody.getHtmlContent(), "href"));
  }

  @Test
  public void testEmailIsAnnotatedAsync() throws SecurityException, NoSuchMethodException {
    Annotation asynch =
        EmailBroadcast.class
            .getMethod(
                "sendHtmlEmail", String.class, EmailContent.class, List.class, Communication.class)
            .getAnnotation(Async.class);
    assertNotNull(asynch);

    Annotation asynch2 =
        EmailBroadcast.class
            .getMethod("sendTextEmail", String.class, String.class, List.class, Communication.class)
            .getAnnotation(Async.class);
    assertNotNull(asynch2);
  }

  @Test
  public void testExceptionLogging() {
    // set up logger whose contents we can inspect
    StringAppenderForTestLogging testStringAppender =
        CoreTestUtils.configureStringLogger(LogManager.getLogger(FailedEmailLogger.class));

    AuthFailureEmailSenderTSS broadcast = new AuthFailureEmailSenderTSS();
    broadcast.init();
    broadcast.sendHtmlEmail("any", anyHtmlBody(), Collections.emptyList(), null);
    String firstMessage = testStringAppender.logContents;
    assertTrue(
        "unexpected content: " + firstMessage,
        firstMessage.startsWith(EmailBroadcastImp.AUTHENTICATION_FAILURE_PREFIX));

    testStringAppender.clearLog();
    SendFailureEmailSenderTSS broadcast2 = new SendFailureEmailSenderTSS();
    broadcast2.init();
    broadcast2.sendHtmlEmail("any", anyHtmlBody(), Collections.emptyList(), null);
    firstMessage = testStringAppender.logContents;
    assertTrue(
        "unexpected content: " + firstMessage,
        firstMessage.startsWith(EmailBroadcastImp.SEND_FAILURE_PREFIX));

    testStringAppender.clearLog();
    GenericFailureEmailSenderTSS broadcast3 = new GenericFailureEmailSenderTSS();
    broadcast3.init();
    broadcast3.sendHtmlEmail("any", anyHtmlBody(), Collections.emptyList(), null);
    firstMessage = testStringAppender.logContents;
    assertTrue(
        "unexpected content: " + firstMessage,
        firstMessage.startsWith(EmailBroadcastImp.GENERAL_FAILURE_PREFIX));
  }

  @Test
  public void retryConfigDoesntRetryAuthenticationFailedException() {
    RetryConfig cfg = broadcast.buildRetryConfig();
    assertFalse(cfg.getExceptionPredicate().test(new AuthenticationFailedException()));
    assertTrue(cfg.getExceptionPredicate().test(new MessagingException()));
  }

  @Test
  public void partitionEmailsByAddressLimit() {
    // send in batches of 3, count how many distinct emails are made
    EmailSenderTSS senderTss = new EmailSenderTSS();
    senderTss.init();
    senderTss.setAddressChunkSize(3);

    User sender = createSender();
    MessageOrRequest mor = TestFactory.createAnyMessage(sender);
    mor.setMessage("message1");
    mor.setRecipients(createNAddresses(7));
    senderTss.broadcast(mor);
    assertEquals(3, senderTss.messageCount);

    senderTss.messageCount = 0;
    mor.setRecipients(createNAddresses(3));
    senderTss.broadcast(mor);
    assertEquals(1, senderTss.messageCount);
    // nothing sent ifn
    senderTss.messageCount = 0;
    mor.setRecipients(createNAddresses(0));
    senderTss.broadcast(mor);
    assertEquals(0, senderTss.messageCount);
  }

  /*
   * Set up a rate limiter that will only handle 2 requests, and won't wait
   * for the tokens to renew. We attempt to send 3 emails, 3rd one throws exception
   */
  @Test
  public void rateLimiterTestFailure() throws Exception {
    EmailSenderTSS senderTss = new EmailSenderTSS();
    senderTss.init();
    senderTss.setAddressChunkSize(3);

    RateLimiterConfig cfg =
        RateLimiterConfig.custom()
            .limitForPeriod(2)
            .limitRefreshPeriod(Duration.of(100, ChronoUnit.MILLIS))
            .timeoutDuration(Duration.of(1, ChronoUnit.MILLIS))
            .build();
    RateLimiter rl = RateLimiter.of("emailLimiter", cfg);
    senderTss.setRateLimiter(rl); // max 2 per 100 millis for testing
    User sender = createSender();
    MessageOrRequest mor = TestFactory.createAnyMessage(sender);
    mor.setMessage("message1");
    mor.setRecipients(createNAddresses(7));
    assertExceptionThrown(() -> senderTss.broadcast(mor), RequestNotPermitted.class);
  }

  // in this test, we allow a timeout duration long enough to allow tokens to refresh
  @Test
  public void rateLimiterTestSuccess() throws Exception {
    EmailSenderTSS senderTss = new EmailSenderTSS();
    senderTss.init();
    senderTss.setAddressChunkSize(3);

    RateLimiterConfig cfg =
        RateLimiterConfig.custom()
            .limitForPeriod(2)
            .limitRefreshPeriod(Duration.of(100, ChronoUnit.MILLIS))
            .timeoutDuration(Duration.of(1000, ChronoUnit.MILLIS))
            .build();
    RateLimiter rl = RateLimiter.of("emailLimiter", cfg);
    senderTss.setRateLimiter(rl); // max 2 per 100 millis for testing
    User sender = createSender();
    MessageOrRequest mor = TestFactory.createAnyMessage(sender);
    mor.setMessage("message1");
    mor.setRecipients(createNAddresses(7));
    senderTss.broadcast(mor);
    assertEquals(3, senderTss.messageCount);
  }

  private Set<CommunicationTarget> createNAddresses(int numAddresses) {
    return IntStream.range(0, numAddresses)
        .mapToObj(i -> TestFactory.createAnyUser(getRandomAlphabeticString("i")))
        .map(this::target)
        .collect(Collectors.toSet());
  }

  CommunicationTarget target(User recipient) {
    CommunicationTarget ct = new CommunicationTarget();
    ct.setRecipient(recipient);
    return ct;
  }

  private EmailContent anyHtmlBody() {
    return EmailContent.builder().htmlContent("<p>body</p>").plainTextContent("body").build();
  }
}
