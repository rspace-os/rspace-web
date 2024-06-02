package com.researchspace.webapp.controller;

import com.researchspace.model.User;
import com.researchspace.service.IVerificationPasswordValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("verificationPasswordResetHandler")
public class VerificationPasswordResetHandler extends PasswordChangeHandlerBase {

  private @Autowired IVerificationPasswordValidator verificationPasswordValidator;

  @Override
  protected boolean reauthenticate(User user, String currentPassword) {
    return verificationPasswordValidator.authenticateVerificationPassword(user, currentPassword);
  }

  @Override
  protected User encryptAndSavePassword(User user, String newpassword) {
    String encryptedPass = verificationPasswordValidator.hashVerificationPassword(newpassword);
    user.setVerificationPassword(encryptedPass);
    return userManager.saveUser(user);
  }
}
