package com.researchspace.webapp.controller;

import com.researchspace.model.TokenBasedVerification;
import org.springframework.stereotype.Component;

/**
 * A specific subclass for template method to reset verification password by email in SSO
 * environment
 */
@Component
public class VerificationPasswordResetByEmailHandler extends PasswordResetByEmailHandlerBase {
  private static final String resetLinkFormat = "%s/vfpwd/verificationPasswordResetReply?token=%s";
  private static final String passwordType = PasswordType.VERIFICATION_PASSWORD.toString();
  private static final String completionEmailSubjectKey =
      "email.account.verificationPasswordResetComplete.subject";
  private static final String emailSubjectKey = "email.account.passwordResetMessage.subject";

  @Override
  TokenBasedVerification applyPasswordChange(PasswordResetCommand cmd) {
    return userManager.applyVerificationPasswordChange(cmd.getPassword(), cmd.getToken());
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
