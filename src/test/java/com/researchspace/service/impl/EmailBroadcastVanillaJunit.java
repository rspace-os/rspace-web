package com.researchspace.service.impl;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.EmailContent;
import com.researchspace.testutils.TestFactory;
import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.junit.Test;

public class EmailBroadcastVanillaJunit {
  // this oracle comes from the library class whose function we are testing.
  // a little circular but will detect regressions.
  static final FastDateFormat EMAIL_DATE_FORMAT = DateFormatUtils.SMTP_DATETIME_FORMAT;
  EmailBroadcastImpl emailerBroadcastImpl =
      new EmailBroadcastImpl(new EmailContentGenerator(), "http://localhost:8080");

  @Test
  public void calculateRecipients() {
    User sender = TestFactory.createAnyUser("sender");
    User enabledUser = TestFactory.createAnyUser("r1");
    User disabledUser = TestFactory.createAnyUser("r2");
    disabledUser.setEnabled(false);
    MessageOrRequest mf = TestFactory.createAnyMessage(sender);
    CommunicationTarget ct1 = new CommunicationTarget();
    ct1.setRecipient(enabledUser);
    CommunicationTarget ct2 = new CommunicationTarget();
    ct2.setRecipient(disabledUser);

    mf.addRecipient(ct1);
    mf.addRecipient(ct2);
    List<String> addresses = new ArrayList<>();
    emailerBroadcastImpl.addRecipients(mf, addresses, mf.getRecipients());

    assertEquals(1, addresses.size());
    assertEquals(enabledUser.getEmail(), addresses.get(0));
  }

  @Test
  public void testDateHeaderMatchesRFC2822() throws MessagingException, ParseException {
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    MimeMessage message = new MimeMessage(Session.getInstance(System.getProperties(), null));
    emailerBroadcastImpl.setDateHeader(message, now);
    // parse date
    Date parsedHeader = EMAIL_DATE_FORMAT.parse(message.getHeader("Date")[0]);
    // ignore nanos as these get discarded in email Header
    assertEquals(now, Instant.ofEpochMilli(parsedHeader.getTime()));
  }

  @Test
  public void createMessagePart() throws MessagingException, ParseException, IOException {
    EmailBroadcastImpl.EmailConfig plainTextConfig =
        new EmailBroadcastImpl.EmailConfig(
            List.of(), "subject", new EmailContent(null, "<html>hello</html", "hello"), null);
    Multipart multi = emailerBroadcastImpl.generateMultipartContent(plainTextConfig);
    assertTrue(multi.getBodyPart(0).getContentType().startsWith("text/plain"));
    assertEquals("text/html; charset=UTF-8", multi.getBodyPart(1).getContentType());
    assertEquals("hello", multi.getBodyPart(0).getContent().toString());
  }

  @Test
  public void emailContentRejectsMissingPlainText() {
    assertThrows(
        NullPointerException.class, () -> new EmailContent(null, "<html>hello</html>", null));
  }

  @Test
  public void emailConfigFiltersImmutableRecipientListsWithoutMutatingThem() {
    List<String> recipients =
        List.of("user@example.com", "missing" + EmailBroadcast.UNKNOWN_EMAIL_SUFFIX);

    EmailBroadcastImpl.EmailConfig config =
        new EmailBroadcastImpl.EmailConfig(
            recipients, "subject", new EmailContent(null, "<p>hello</p>", "hello"), null);

    assertEquals(List.of("user@example.com"), config.addresses());
    assertEquals(2, recipients.size());
  }
}
