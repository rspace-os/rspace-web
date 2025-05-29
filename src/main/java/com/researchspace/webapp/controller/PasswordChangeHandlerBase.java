package com.researchspace.webapp.controller;

import com.researchspace.core.util.RequestUtil;
import com.researchspace.model.User;
import com.researchspace.model.dtos.UserValidator;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.service.UserManager;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/** Abstract class for code common to login password/ verification password handling */
public abstract class PasswordChangeHandlerBase {

  protected static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);
  protected @Autowired UserManager userManager;
  private @Autowired UserValidator validator;

  private boolean checkInputString(String str) {
    return StringUtils.isBlank(str);
  }

  public String changePassword(
      String currentPassword,
      String newPassword,
      String confirmPassword,
      HttpServletRequest request,
      User user) {

    String currentpass = StringUtils.trim(currentPassword);
    String newpass = StringUtils.trim(newPassword);
    String confirmpass = StringUtils.trim(confirmPassword);

    if (checkInputString(currentpass)
        || checkInputString(newpass)
        || checkInputString(confirmpass)) {
      return "Please enter data in all fields";
    }

    if (!reauthenticate(user, currentpass)) {
      logAuthenticationFailure(request, user);
      return "The current password is incorrect";
    }

    String checkPasswordResult =
        validator.validatePasswords(newPassword, confirmPassword, user.getUsername());
    if (!UserValidator.FIELD_OK.equals(checkPasswordResult)) {
      logAuthenticationFailure(request, user);
      return checkPasswordResult;
    }

    encryptAndSavePassword(user, newpass);
    SECURITY_LOG.info("[{}] successfully reset their password", user.getUsername());
    return "Password changed successfully";
  }

  private void logAuthenticationFailure(HttpServletRequest request, User user) {
    SECURITY_LOG.warn(
        "Unsuccessfully attempted to reset password for [{}], from {}",
        user.getUsername(),
        RequestUtil.remoteAddr(request));
  }

  protected abstract boolean reauthenticate(User user, String currentPassword);

  protected abstract User encryptAndSavePassword(User user, String newpassword);
}
