package com.researchspace.service.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.researchspace.auth.password.BoundedPasswordVerifier;
import com.researchspace.auth.password.RSpacePasswordEncoder;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.IVerificationPasswordValidator;
import com.researchspace.service.UserManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

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

  @Override
  public boolean authenticateVerificationPassword(User passwordOwner, String password) {
    String username = passwordOwner.getUsername();
    return verifier.verifyAndUpgrade(
        username,
        password,
        passwordOwner.getVerificationPassword(),
        (verified, upgraded) ->
            userMgr.upgradeVerificationPasswordHash(username, verified, upgraded),
        // keep the caller's copy in step, or a later save would write the old hash back
        passwordOwner::setVerificationPassword);
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
