package com.researchspace.service.impl;

import com.researchspace.model.comms.Communication;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.impl.EmailBroadcastImp.EmailContent;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dummy email sender for use in testing /development. <br>
 * This does not send real emails - just logs that email-sending methods were called.
 */
public class DevEmailSenderImpl implements EmailBroadcast, ConfigurableLogger {
  Logger defaultLogger = LoggerFactory.getLogger(DevEmailSenderImpl.class);
  Logger log = defaultLogger;

  @Override
  public void sendTextEmail(String subj, String txt, List<String> recipients, Communication comm) {
    log.info(
        "DUMMY EMAIL: Text Email [{}] with subject [{}] sent to [{}]",
        txt,
        subj,
        Arrays.toString(recipients.toArray()));
  }

  @Override
  public void sendHtmlEmail(
      String subj, EmailContent content, List<String> recipients, Communication comm) {
    log.info(
        "HTML email (has plainText? {})  with subject '{}' sent to [{}]. Content: [{}]",
        content.getPlainTextContent().isPresent(),
        subj,
        Arrays.toString(recipients.toArray()),
        content.getHtmlContent());
  }

  @Override
  public EmailContent generateEmailBody(Communication comm) {
    String rc = "generated msg";
    log.info(rc);
    return EmailContent.builder().htmlContent("<p>" + rc + "</p>").plainTextContent(rc).build();
  }

  /**
   * Overrides default logger (for testing purposes)
   *
   * @param log
   */
  public void setLogger(Logger log) {
    this.log = log;
  }

  public Logger setLoggerDefault() {
    this.log = defaultLogger;
    return this.log;
  }
}
