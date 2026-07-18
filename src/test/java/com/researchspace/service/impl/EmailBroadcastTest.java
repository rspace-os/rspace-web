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
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.EmailContent;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestFactory;
import io.github.resilience4j.retry.RetryConfig;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;
import javax.mail.AuthenticationFailedException;
import javax.mail.IllegalWriteException;
import javax.mail.MessagingException;
import javax.mail.SendFailedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;

public class EmailBroadcastTest extends SpringTransactionalTest {

  private static final String BASEURL = "http://localhost:8080/";

  // Removes retries (which aren't tested in this class) from stubs to avoid waiting during tests
  abstract static class NoRetryBroadcasterStub extends EmailBroadcastImpl {
    @Override
    RetryConfig buildRetryConfig() {
      return RetryConfig.custom().maxAttempts(1).build();
    }
  }

  static class FailingBroadcasterStub extends NoRetryBroadcasterStub {
    private final MessagingException failure;

    FailingBroadcasterStub(MessagingException failure) {
      this.failure = failure;
    }

    @Override
    protected void sendMailToAddresses(EmailConfig config) throws MessagingException {
      throw failure;
    }
  }

  static class EmailSenderStub extends EmailBroadcastImpl {
    int messageCount = 0;

    @Override
    protected void sendMailToAddresses(EmailConfig config) {
      messageCount++;
    }
  }

  private @Autowired EmailContentGenerator emailContentGenerator;

  private CommunicationEmailContentGenerator contentGenerator;

  @Before
  public void setUp() {
    // we need to explicitly create this, as it is only created by Spring in 'prod' profile
    // and these tests run in dev profile.
    contentGenerator = new CommunicationEmailContentGenerator(emailContentGenerator, BASEURL);
  }

  @Test
  public void testGenerateEmailForSimpleMessage() {
    User sender = createSender();
    MessageOrRequest mor = TestFactory.createAnyMessage(sender);
    mor.setMessage("message1");
    Record record = TestFactory.createAnyRecord(sender);
    record.setId(1L);
    mor.setRecord(record);
    EmailContent msgbody = contentGenerator.generate(mor);

    assertEquals("RSpace message", msgbody.subject());
    assertAllVelocityVarsReplaced(msgbody.htmlContent());
    assertAllVelocityVarsReplaced(msgbody.plainTextContent());
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
    EmailContent msgbody = contentGenerator.generate(mor);

    assertEquals("RSpace request", msgbody.subject());
    assertAllVelocityVarsReplaced(msgbody.htmlContent());
    assertTrue(msgbody.htmlContent().contains(BASEURL + "/workspace/editor/structuredDocument/1"));

    // now try with a notebook and assert the link is different:
    assertNotebookLinkGenerated(sender, mor);
  }

  private void assertNotebookLinkGenerated(User sender, Communication mor) {
    Notebook nb = TestFactory.createANotebook("any", sender);
    nb.setId(1L);
    mor.setRecord(nb);
    EmailContent msgbody = contentGenerator.generate(mor);
    assertAllVelocityVarsReplaced(msgbody.htmlContent());
    assertTrue(msgbody.htmlContent().contains(BASEURL + "/notebookEditor/1"));
  }

  @Test
  public void testGenerateEmailForNotification() {
    User sender = createSender();

    Record record = TestFactory.createAnyRecord(sender);
    Notification not = TestFactory.createANotification(sender);

    not.setRecord(record);
    record.setId(1L);
    EmailContent msgbody = contentGenerator.generate(not);
    assertEquals("RSpace notification", msgbody.subject());
    assertEquals(2, StringUtils.countMatches(msgbody.htmlContent(), "href"));

    assertAllVelocityVarsReplaced(msgbody.htmlContent());
    // now try with a notebook and assert the link is different:
    assertNotebookLinkGenerated(sender, not);

    // now use a notification type that shouldn't include link to doc;
    not.setNotificationType(NotificationType.NOTIFICATION_DOCUMENT_DELETED);
    msgbody = contentGenerator.generate(not);
    assertAllVelocityVarsReplaced(msgbody.htmlContent());
    // no record link
    assertEquals(1, StringUtils.countMatches(msgbody.htmlContent(), "href"));
  }

  @Test
  public void testEmailIsAnnotatedAsync() throws SecurityException, NoSuchMethodException {
    Annotation asynch =
        EmailBroadcast.class
            .getMethod("sendEmail", EmailContent.class, List.class, Communication.class)
            .getAnnotation(Async.class);
    assertNotNull(asynch);
  }

  @Test
  public void testExceptionLogging() {
    // set up logger whose contents we can inspect
    StringAppenderForTestLogging testStringAppender =
        CoreTestUtils.configureStringLogger(LogManager.getLogger(FailedEmailLogger.class));

    FailingBroadcasterStub broadcast =
        new FailingBroadcasterStub(new AuthenticationFailedException("test auth failure"));
    broadcast.init();
    broadcast.sendEmail(anyHtmlBody(), List.of("user@example.com"), null);
    String firstMessage = testStringAppender.logContents;
    assertTrue(
        "unexpected content: " + firstMessage,
        firstMessage.startsWith(EmailBroadcastImpl.AUTHENTICATION_FAILURE_PREFIX));

    testStringAppender.clearLog();
    FailingBroadcasterStub broadcast2 =
        new FailingBroadcasterStub(new SendFailedException("test send failure"));
    broadcast2.init();
    broadcast2.sendEmail(anyHtmlBody(), List.of("user@example.com"), null);
    firstMessage = testStringAppender.logContents;
    assertTrue(
        "unexpected content: " + firstMessage,
        firstMessage.startsWith(EmailBroadcastImpl.SEND_FAILURE_PREFIX));

    testStringAppender.clearLog();
    FailingBroadcasterStub broadcast3 =
        new FailingBroadcasterStub(new IllegalWriteException("test send failure"));
    broadcast3.init();
    broadcast3.sendEmail(anyHtmlBody(), List.of("user@example.com"), null);
    firstMessage = testStringAppender.logContents;
    assertTrue(
        "unexpected content: " + firstMessage,
        firstMessage.startsWith(EmailBroadcastImpl.GENERAL_FAILURE_PREFIX));
  }

  @Test
  public void disabledRecipientsAreNotEmailed() {
    EmailSenderStub emailSender = new EmailSenderStub();
    emailSender.init();
    MessageOrRequest message = TestFactory.createAnyMessage(createSender());
    User disabledRecipient = TestFactory.createAnyUser("disabled");
    disabledRecipient.setEnabled(false);
    message.setRecipients(Set.of(target(disabledRecipient)));

    broadcasterFor(emailSender).broadcast(message);

    assertEquals(0, emailSender.messageCount);
  }

  CommunicationTarget target(User recipient) {
    CommunicationTarget ct = new CommunicationTarget();
    ct.setRecipient(recipient);
    return ct;
  }

  private EmailContent anyHtmlBody() {
    return new EmailContent("subject", "<p>body</p>", "body");
  }

  private CommunicationEmailBroadcaster broadcasterFor(EmailBroadcast emailSender) {
    return new CommunicationEmailBroadcaster(emailSender, emailContentGenerator, BASEURL);
  }
}
