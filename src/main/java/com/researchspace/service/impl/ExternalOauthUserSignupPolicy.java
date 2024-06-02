package com.researchspace.service.impl;

import com.researchspace.model.User;
import com.researchspace.service.ISignupHandlerPolicy;
import com.researchspace.service.UserExistsException;
import com.researchspace.service.UserManager;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectRetrievalFailureException;

/** Signup policy for when Signing up using an external ID provider. */
public class ExternalOauthUserSignupPolicy implements ISignupHandlerPolicy {

  @Autowired private UserManager userManager;

  public void setUserManager(UserManager userManager) {
    this.userManager = userManager;
  }

  private Logger log = LoggerFactory.getLogger(ExternalOauthUserSignupPolicy.class);

  @Override
  public User saveUser(User newUser, HttpServletRequest request) throws UserExistsException {
    try {
      // happy case - unknown user, can just signup and login.
      newUser = userManager.saveNewUser(newUser);
    } catch (UserExistsException uee) {
      User sameuserByEmail = null;
      boolean isSameUserName = false;
      boolean isSameEmail = false;
      // since user exists, either email or username must be already existing
      List<User> usersByEmail = userManager.getUserByEmail(newUser.getEmail());
      if (!usersByEmail.isEmpty()) {
        sameuserByEmail = usersByEmail.get(0);
        isSameEmail = true;
      } else {
        log.warn("No user found with email [{}]", newUser.getEmail());
      }
      if (isSameEmail) {
        return sameuserByEmail;
      }
      // in which case, must be matching on username but not email...
      try {
        userManager.getUserByUsername(newUser.getUsername());
        isSameUserName = true;
      } catch (ObjectRetrievalFailureException orfe) {
        // this is unlikely to happen, so just log
        log.warn("No user found with username [{}]", newUser.getUsername());
      }
      // now we handle scenarios.
      // 1. email exists - we'll login
      if (isSameUserName) {
        throw uee;
      } else {
        String message =
            String.format(
                "Couldn't save new user %s,  but neither email '%s' or username '%s' already exist."
                    + " This is an unexpected error.",
                newUser.getUniqueName(), newUser.getEmail(), newUser.getUsername());
        log.error(message);
        throw new IllegalStateException(message);
      }
    }
    return newUser;
  }
}
