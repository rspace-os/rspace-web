package com.researchspace.service.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.researchspace.auth.password.BoundedPasswordVerifier;
import com.researchspace.auth.password.RSpacePasswordEncoder;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.IVerificationPasswordValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class VerificationPasswordValidatorImpl implements IVerificationPasswordValidator {

  protected @Autowired IPropertyHolder properties;
  private @Autowired RSpacePasswordEncoder passwordEncoder;
  private @Autowired BoundedPasswordVerifier verifier;

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

  @Override
  public boolean authenticateVerificationPassword(User passwordOwner, String password) {
    String username = passwordOwner.getUsername();
    try {
      return verifier.verify(username, password, passwordOwner.getVerificationPassword());
    } catch (IllegalArgumentException e) {
      log.error("Stored verification password of [{}] cannot be verified", username, e);
      return false;
    }
  }

  /**
   * Hashes verification password for storage.
   *
   * @param Plain text password
   * @return Hashed value of password
   */
  @Override
  public String hashVerificationPassword(String password) {
    return passwordEncoder.encode(password);
  }
}
