package com.researchspace.webapp.controller;

import com.researchspace.model.User;
import com.researchspace.service.IReauthenticator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("loginPasswordResetHandler")
public class LoginPasswordResetHandler extends PasswordChangeHandlerBase {

  private @Autowired IReauthenticator reauthenticator;

  @Override
  protected boolean reauthenticate(User user, String currentPassword) {
    return reauthenticator.reauthenticate(user, currentPassword);
  }

  @Override
  protected User encryptAndSavePassword(User user, String newpassword) {
    user.setPassword(newpassword);
    return userManager.saveUser(user);
  }
}
