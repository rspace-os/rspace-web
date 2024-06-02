package com.researchspace.service.impl;

import com.researchspace.model.User;
import com.researchspace.service.ISignupHandlerPolicy;
import com.researchspace.service.UserExistsException;
import com.researchspace.service.UserManager;
import javax.servlet.http.HttpServletRequest;

/**
 * For use in non-cloud environment - simply delegates through to underlying {@link UserManager}
 * saveNewUser method.
 */
public class DefaultUserSignupPolicy extends AbstractUserSignupPolicy
    implements ISignupHandlerPolicy {

  @Override
  public User saveUser(User userFromForm, HttpServletRequest request) throws UserExistsException {
    if (properties.isCloud()) {
      throw new IllegalStateException(
          "Default user signup handler shouldn't be used in a cloud deployment!");
    }
    User user = userMgr.saveNewUser(userFromForm);
    return user;
  }
}
