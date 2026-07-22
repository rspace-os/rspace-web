package com.researchspace.service.impl;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.comms.Communication;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.EmailContent;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.junit.Test;
import org.springframework.scheduling.annotation.Async;

public class EmailBroadcastVanillaJunit {
  // this oracle comes from the library class whose function we are testing.
  // a little circular but will detect regressions.
  static final FastDateFormat EMAIL_DATE_FORMAT = DateFormatUtils.SMTP_DATETIME_FORMAT;
  EmailBroadcastImpl emailerBroadcastImpl = new EmailBroadcastImpl(5, 25);

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
            List.of(), new EmailContent("subject", "<html>hello</html", "hello"), null);
    Multipart multi = emailerBroadcastImpl.generateMultipartContent(plainTextConfig);
    assertTrue(multi.getBodyPart(0).getContentType().startsWith("text/plain"));
    assertEquals("text/html; charset=UTF-8", multi.getBodyPart(1).getContentType());
    assertEquals("hello", multi.getBodyPart(0).getContent().toString());
  }

  @Test
  public void emailContentRejectsMissingPlainText() {
    assertThrows(
        NullPointerException.class, () -> new EmailContent("subject", "<html>hello</html>", null));
  }

  @Test
  public void emailContentRejectsMissingSubject() {
    assertThrows(
        NullPointerException.class, () -> new EmailContent(null, "<html>hello</html>", "hello"));
  }

  @Test
  public void sendEmailIsAnnotatedAsync() throws NoSuchMethodException {
    Annotation asynch =
        EmailBroadcast.class
            .getMethod("sendEmail", EmailContent.class, List.class, Communication.class)
            .getAnnotation(Async.class);
    assertNotNull(asynch);
  }

  @Test
  public void emailConfigFiltersImmutableRecipientListsWithoutMutatingThem() {
    List<String> recipients =
        List.of("user@example.com", "missing" + EmailBroadcast.UNKNOWN_EMAIL_SUFFIX);

    EmailBroadcastImpl.EmailConfig config =
        new EmailBroadcastImpl.EmailConfig(
            recipients, new EmailContent("subject", "<p>hello</p>", "hello"), null);

    assertEquals(List.of("user@example.com"), config.addresses());
    assertEquals(2, recipients.size());
  }
}
