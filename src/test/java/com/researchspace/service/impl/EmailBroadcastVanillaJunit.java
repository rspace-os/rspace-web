package com.researchspace.service.impl;

import static com.researchspace.service.impl.EmailBroadcastImp.TEXT_ONLY_EMAIL_DEFAULT;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.impl.EmailBroadcastImp.EmailContent;
import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;
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
  EmailBroadcastImp emailerBroadcastImp = new EmailBroadcastImp();

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
    emailerBroadcastImp.addRecipients(mf, addresses, mf.getRecipients());

    assertEquals(1, addresses.size());
    assertTrue(addresses.get(0).equals(enabledUser.getEmail()));
  }

  @Test
  public void testDateHeaderMatchesRFC2822() throws MessagingException, ParseException {
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    MimeMessage message = createMimeMessage();
    emailerBroadcastImp.setDateHeader(message, now);
    // parse date
    Date parsedHeader = EMAIL_DATE_FORMAT.parse(message.getHeader("Date")[0]);
    // ignore nanos as these get discarded in email Header
    assertEquals(now, Instant.ofEpochMilli(parsedHeader.getTime()));
  }

  @Test
  public void createMessagePart() throws MessagingException, ParseException, IOException {
    EmailBroadcastImp.EmailConfig plainTextConfig = createHtmlAndPlainTextEmailContent();
    Multipart multi = emailerBroadcastImp.generateMultipartContent(plainTextConfig);
    assertEquals("text/plain", multi.getBodyPart(0).getContentType());
    assertEquals("text/html", multi.getBodyPart(1).getContentType());
    assertTrue(multi.getBodyPart(0).getContent().toString().equals("hello"));
  }

  @Test
  public void createMessagePartShowsDefaultTextIfNotSet()
      throws MessagingException, ParseException, IOException {
    EmailBroadcastImp.EmailConfig plainTextConfig = createHtmlOnlyEmailContent();
    Multipart multi = emailerBroadcastImp.generateMultipartContent(plainTextConfig);
    assertTrue(multi.getBodyPart(0).getContent().toString().equals(TEXT_ONLY_EMAIL_DEFAULT));
  }

  private EmailBroadcastImp.EmailConfig createHtmlOnlyEmailContent() {
    EmailBroadcastImp.EmailConfig plainTextConfig =
        new EmailBroadcastImp.EmailConfig(
            Collections.emptyList(),
            "subject",
            EmailContent.builder().htmlContent("<html>hello</html").build(),
            null,
            true);
    return plainTextConfig;
  }

  private EmailBroadcastImp.EmailConfig createHtmlAndPlainTextEmailContent() {
    EmailBroadcastImp.EmailConfig plainTextConfig =
        new EmailBroadcastImp.EmailConfig(
            Collections.emptyList(),
            "subject",
            EmailContent.builder()
                .plainTextContent("hello")
                .htmlContent("<html>hello</html")
                .build(),
            null,
            true);
    return plainTextConfig;
  }

  private MimeMessage createMimeMessage() {
    Properties props = System.getProperties();
    Session session = Session.getInstance(props, null);
    MimeMessage message = new MimeMessage(session);
    return message;
  }
}
