package com.researchspace.service.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.researchspace.auth.password.BoundedPasswordVerifier;
import com.researchspace.auth.password.RSpacePasswordEncoder;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.IVerificationPasswordValidator;
import com.researchspace.service.UserManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class VerificationPasswordValidatorImpl implements IVerificationPasswordValidator {

  protected @Autowired IPropertyHolder properties;
  private @Autowired RSpacePasswordEncoder passwordEncoder;
  private @Autowired BoundedPasswordVerifier verifier;
  // UserManagerImpl depends on this class
  private @Autowired @Lazy UserManager userMgr;

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
    String username = passwordOwner.getUsername();
    String stored = passwordOwner.getVerificationPassword();
    BoundedPasswordVerifier.Result result;
    try {
      result = verifier.verify(username, password, stored);
    } catch (IllegalArgumentException e) {
      log.error("Verification password of [{}] cannot be verified", username, e);
      return false;
    }
    if (result.upgradedHash() != null) {
      storeUpgrade(passwordOwner, stored, result.upgradedHash());
    }
    return result.matches();
  }

  private void storeUpgrade(User passwordOwner, String oldHash, String newHash) {
    String username = passwordOwner.getUsername();
    try {
      if (!userMgr.upgradeVerificationPasswordHash(username, oldHash, newHash)) {
        log.info("Verification password of [{}] changed during verification", username);
        return;
      }
    } catch (RuntimeException e) {
      log.warn("Could not store upgraded verification password of [{}]", username, e);
      return;
    }
    // keep the caller's copy in step, or a later save would write the old hash back
    passwordOwner.setVerificationPassword(newHash);
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
