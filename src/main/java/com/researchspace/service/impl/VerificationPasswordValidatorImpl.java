package com.researchspace.service.impl;

import static org.apache.commons.lang.StringUtils.isBlank;

import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.IVerificationPasswordValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;

@Component
public class VerificationPasswordValidatorImpl implements IVerificationPasswordValidator {

  protected @Autowired IPropertyHolder properties;

  @Override
  public boolean isVerificationPasswordSet(User user) {
    if (isVerificationPasswordRequired(user)) {
      return !isBlank(user.getVerificationPassword());
    }
    return true;
  }

  @Override
  public boolean isVerificationPasswordRequired(User user) {
    return isSSONonBackdoorUser(user) || isCommunity3rdPartyLogin(user);
  }

  private boolean isSSONonBackdoorUser(User user) {
    return properties.isSSO() && !SignupSource.SSO_BACKDOOR.equals(user.getSignupSource());
  }

  private boolean isCommunity3rdPartyLogin(User user) {
    return SignupSource.GOOGLE.equals(user.getSignupSource());
  }

  /**
   * Checks if user's verification password has been set to a valid value.
   *
   * @param The principal user or sysadmin operating-as
   * @return true if current verification password is valid, false otherwise
   */
  @Override
  public boolean authenticateVerificationPassword(User passwordOwner, String password) {
    String hashedPassword = passwordOwner.getVerificationPassword();
    return BCrypt.checkpw(password, hashedPassword);
  }

  /**
   * Hashes verification password for storage.
   *
   * @param Plain text password
   * @return Hashed value of password
   */
  @Override
  public String hashVerificationPassword(String password) {
    return BCrypt.hashpw(password, BCrypt.gensalt());
  }
}
