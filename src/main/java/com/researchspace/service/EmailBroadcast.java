package com.researchspace.service;

import com.researchspace.model.comms.Communication;
import com.researchspace.service.impl.EmailBroadcastImp.EmailContent;
import java.util.List;
import org.springframework.scheduling.annotation.Async;

/** Encapsulates sending of emails. */
public interface EmailBroadcast {

  String UNKNOWN_EMAIL_SUFFIX = "-unknown@researchspace.com";

  /**
   * Send email in plain text
   *
   * @param txt plain text
   * @param recipients emailAddress List
   */
  @Async(value = "emailTaskExecutor")
  void sendTextEmail(String subj, String txt, List<String> recipients, Communication comm);

  /**
   * Send email in HTML and plain text alternative for maximum spam-safety
   *
   * @param content EmailContent should contain plaintext and html parts : contains <html><body>
   *     tag, can add link as well
   * @param recipients : a list of email addresses of recipients
   */
  @Async(value = "emailTaskExecutor")
  void sendHtmlEmail(
      String subj, EmailContent content, List<String> recipients, Communication comm);

  /**
   * Generates a String message from a communication
   *
   * @param comm
   * @return
   */
  EmailContent generateEmailBody(Communication comm);
}
