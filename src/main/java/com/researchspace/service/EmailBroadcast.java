package com.researchspace.service;

import com.researchspace.model.comms.Communication;
import java.util.List;
import org.springframework.scheduling.annotation.Async;

/** Encapsulates sending of emails. */
public interface EmailBroadcast {

  String UNKNOWN_EMAIL_SUFFIX = "-unknown@researchspace.com";

  /**
   * Send email in HTML and plain text alternative for maximum spam-safety
   *
   * @param content complete HTML and plain-text alternatives
   * @param recipients : a list of email addresses of recipients
   */
  @Async(value = "emailTaskExecutor")
  void sendHtmlEmail(
      String subj, EmailContent content, List<String> recipients, Communication comm);
}
