package com.researchspace.service;

import com.researchspace.model.comms.Communication;
import java.util.List;
import org.springframework.scheduling.annotation.Async;

/** Encapsulates sending of emails. */
public interface EmailBroadcast {

  String UNKNOWN_EMAIL_SUFFIX = "-unknown@researchspace.com";

  /** Sends a complete rendered email to the supplied recipients. */
  @Async(value = "emailTaskExecutor")
  void sendEmail(EmailContent content, List<String> recipients, Communication comm);
}
