package com.researchspace.service.cloud.impl;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.core.util.RequestUtil;
import com.researchspace.model.TokenBasedVerification;
import com.researchspace.model.TokenBasedVerificationType;
import com.researchspace.model.User;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.service.ISignupHandlerPolicy;
import com.researchspace.service.UserExistsException;
import com.researchspace.service.cloud.CommunityUserManager;
import com.researchspace.service.impl.AbstractUserSignupPolicy;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * For Community signups, we might be signing up in response to an invitation - so this
 * implementation checks it. This handler is for manual response
 */
public class CommunityManualUserSignupPolicy extends AbstractUserSignupPolicy
    implements ISignupHandlerPolicy {

  private static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  @Autowired private CommunityUserManager communityUserManager;

  @Autowired private AnalyticsManager analyticsManager;

  public void setCloudUserManager(CommunityUserManager cloudUserManager) {
    this.communityUserManager = cloudUserManager;
  }

  public void setAnalyticsManager(AnalyticsManager analyticsMgr) {
    this.analyticsManager = analyticsMgr;
  }

  @Override
  public User saveUser(User userFromForm, HttpServletRequest request) throws UserExistsException {
    if (!properties.isCloud()) {
      throw new IllegalStateException("Cannot invoke as is not a cloud deployment!");
    }

    boolean existingTempUser = communityUserManager.checkTempCloudUser(userFromForm.getEmail());
    if (existingTempUser) {
      return handleInvitedUser(userFromForm, request);
    } else {
      return handleNewUserViaSignupForm(userFromForm, request);
    }
  }

  private User handleNewUserViaSignupForm(User userFromForm, HttpServletRequest request)
      throws UserExistsException {
    // we create a temp user, but they have real credentials so
    // we just need to mark them as temp user.
    userFromForm.setEnabled(false);
    userFromForm.setTempAccount(true);

    return userMgr.saveNewUser(userFromForm);
  }

  private User handleInvitedUser(User userFromForm, HttpServletRequest request)
      throws UserExistsException {

    /* invited user may follow link with valid activation token, but if they didn't,
     * or if the token expired, we still save user details and only defer account activation */

    String providedUsername = userFromForm.getUsername();
    String providedToken = userFromForm.getToken();

    /* user may try to take username of other existing user, or it may be
     * another attempt to create an account after activation link was lost */
    boolean userExists = userMgr.userExists(providedUsername);
    if (userExists) {
      User preexistingUser = userMgr.getUserByUsername(providedUsername);
      if (!userFromForm.getEmail().equals(preexistingUser.getEmail())) {
        UserExistsException exception = new UserExistsException("duplicated username");
        exception.setExistingUsername(true);
        throw exception;
      }
    }

    userFromForm =
        communityUserManager.mergeSignupFormWithTempUser(userFromForm, userFromForm.getEmail());
    userFromForm = userMgr.saveUser(userFromForm);
    userFromForm.setUsername(providedUsername);
    userFromForm.setEnabled(false); // can't login till clicked on activation link
    userFromForm = userMgr.save(userFromForm);
    userFromForm.setToken(null);
    if (!StringUtils.isEmpty(providedToken)) {
      TokenBasedVerification tbv = userMgr.getUserVerificationToken(providedToken);
      if (tbv != null
          && tbv.isValidLink(providedToken, TokenBasedVerificationType.VERIFIED_SIGNUP)) {
        userFromForm = communityUserManager.activateUser(tbv.getToken());
        analyticsManager.userSignedUp(userFromForm, true, request);
        userFromForm.setToken(providedToken);
      } else {
        SECURITY_LOG.warn(
            "Signup attempt with expired verification token for email [{}], from {}",
            userFromForm.getEmail(),
            RequestUtil.remoteAddr(request));
      }
    } else {
      SECURITY_LOG.warn(
          "Signup attempt without verification token for email [{}], from {}",
          userFromForm.getEmail(),
          RequestUtil.remoteAddr(request));
    }

    return userFromForm;
  }
}
