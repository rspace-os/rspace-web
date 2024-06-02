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
  private static final String completionEmailSubject = "RSpace verification password changed ";
  private static final String emailSubject = "RSpace password change request";

  @Override
  TokenBasedVerification applyPasswordChange(PasswordResetCommand cmd) {
    return userManager.applyVerificationPasswordChange(cmd.getPassword(), cmd.getToken());
  }

  String getEmailSubject() {
    return emailSubject;
  }

  String getCompletionEmailSubject() {
    return completionEmailSubject;
  }

  String getPasswordType() {
    return passwordType;
  }

  String getResetLinkFormat() {
    return resetLinkFormat;
  }
}
