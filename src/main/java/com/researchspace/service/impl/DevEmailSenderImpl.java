package com.researchspace.service.impl;

import com.researchspace.model.comms.Communication;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.EmailContent;
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
  public void sendHtmlEmail(
      String subj, EmailContent content, List<String> recipients, Communication comm) {
    log.info(
        "HTML email with subject '{}' sent to [{}]. Content: [{}]",
        subj,
        Arrays.toString(recipients.toArray()),
        content.htmlContent());
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
