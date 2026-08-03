package com.researchspace.webapp.controller;

import com.researchspace.model.TokenBasedVerification;
import org.springframework.stereotype.Component;

/** A specific subclass for template method to reset login password by email */
@Component
public class LoginPasswordResetByEmailHandler extends PasswordResetByEmailHandlerBase {
  private static final String resetLinkFormat = "%s/signup/passwordResetReply?token=%s";
  private static final String passwordType = PasswordType.LOGIN_PASSWORD.toString();
  private static final String completionEmailSubjectKey =
      "email.account.passwordResetComplete.subject";
  private static final String emailSubjectKey = "email.account.passwordResetMessage.subject";

  @Override
  TokenBasedVerification applyPasswordChange(PasswordResetCommand cmd) {
    return userManager.applyLoginPasswordChange(cmd.getPassword(), cmd.getToken());
  }

  String getEmailSubjectKey() {
    return emailSubjectKey;
  }

  String getCompletionEmailSubjectKey() {
    return completionEmailSubjectKey;
  }

  String getPasswordType() {
    return passwordType;
  }

  String getResetLinkFormat() {
    return resetLinkFormat;
  }
}
