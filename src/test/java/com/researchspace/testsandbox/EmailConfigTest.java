package com.researchspace.testsandbox;

import java.util.Properties;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Slf4j
@Ignore
// ignored, unignore to test real email sending
public class EmailConfigTest {
  @Data
  @AllArgsConstructor
  static class MailConfig {
    String user, password, host, port, ssl;
  }

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testSendRealEmail() {

    MailConfig rspaceCfg = createMailtrapConfig();
    testSend(rspaceCfg, "dev@researchspace.com", "<html><body>hello from RSpace.</body></html>");

    MailConfig Cfg = createRSpaceConfig();
    testSend(
        Cfg,
        "dev@researchspace.com",
        "<html><body><p style = 'font-size:75%'>hello</p> from RSpace.</body></html>");
  }

  private MailConfig createRSpaceConfig() {
    return new MailConfig(
        "automated-emails@researchspace.com",
        "<putpasswordhere>",
        "auth.smtp.1and1.co.uk",
        "587",
        "false");
  }

  private MailConfig createMailtrapConfig() {
    return new MailConfig("f7b30fa12c97af", "<putpasswordhere>", "smtp.mailtrap.io", "25", "false");
  }

  private void testSend(MailConfig cfg, String to, String content) {
    Properties props = System.getProperties();
    props.put("mail.smtp.user", cfg.getUser());
    props.put("mail.smtp.password", cfg.getPassword());
    props.put("mail.smtp.host", cfg.getHost());
    props.put("mail.smtp.port", cfg.getPort());
    // props.put("mail.debug", "true");
    props.put("mail.smtp.auth", "true");

    // props.put("mail.smtp.socketFactory.fallback", "true");
    if (Boolean.parseBoolean(cfg.getSsl())) {
      props.put("mail.smtp.starttls.enable", "true");
    }
    // props.put("mail.smtp.EnableSSL.enable", "true");
    // props.put("mail.smtp.ssl.enable", "true");

    Session session = Session.getInstance(props, null);
    session.setDebug(true);
    Transport transport = null;
    MimeMessage message = new MimeMessage(session);
    Multipart multiPart = new MimeMultipart("alternative");

    try {
      MimeBodyPart textPart = new MimeBodyPart();
      textPart.setText("hello from RSpace.", "utf-8");

      MimeBodyPart htmlPart = new MimeBodyPart();
      htmlPart.setContent(content, "text/html; charset=utf-8");
      multiPart.addBodyPart(textPart);
      multiPart.addBodyPart(htmlPart);
      message.setContent(multiPart);
      message.setFrom(new InternetAddress(cfg.getUser()));

      message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));

      message.setSubject("tessa test");

      message.setReplyTo(new Address[] {new InternetAddress("support@researchspace.com")});

      transport = session.getTransport("smtp");
      transport.connect(
          cfg.getHost(), Integer.valueOf(cfg.getPort()), cfg.getUser(), cfg.getPassword());
      transport.sendMessage(message, message.getAllRecipients());

    } catch (MessagingException e) {
      e.printStackTrace();
      log.warn(e.getMessage());
    } finally {
      if (transport != null) {
        try {
          transport.close();
        } catch (MessagingException e) {
          log.error("Transport couldn't be closed: {}", e.getMessage());
        }
      }
    }
  }
}
