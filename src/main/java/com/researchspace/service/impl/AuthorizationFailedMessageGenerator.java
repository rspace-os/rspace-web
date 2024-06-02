package com.researchspace.service.impl;

import com.researchspace.model.User;
import com.researchspace.service.OperationFailedMessageGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/** Convenience class to generate standard messages relating to authorization failures. */
@Component("authorizationFailedMessageGenerator")
public class AuthorizationFailedMessageGenerator implements OperationFailedMessageGenerator {
  @Autowired
  public void setMessages(MessageSource messages) {
    this.messages = messages;
  }

  private MessageSource messages;

  /**
   * GEts a human-readable authorization error string.
   *
   * @param username the username of the user who attempted the unauthorized action
   * @param failedAction A descriptive phrase of the failed action e.g., 'open folder X';
   * @return a String describing the exception.
   */
  @Override
  public String getFailedMessage(String username, String failedAction) {
    String msg =
        messages.getMessage(
            "error.authorization.failure", new String[] {username, failedAction}, null);
    return msg;
  }

  /**
   * Gets a human-readable authorization error string.
   *
   * @param user the user who attempted the unauthorized action
   * @param failedAction A descriptive phrase of the failed action e.g., 'open folder X';
   * @return a String describing the exception.
   */
  @Override
  public String getFailedMessage(User user, String failedAction) {
    return getFailedMessage(user.getUsername(), failedAction);
  }
}
