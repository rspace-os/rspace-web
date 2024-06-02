package com.researchspace.service;

import com.researchspace.model.User;
import javax.servlet.http.HttpServletRequest;

/** Policy or handling signup form submission, dependent on deployments. */
public interface ISignupHandlerPolicy {

  /**
   * Persists a transient {@link User} object created from the signup form.
   *
   * @param userFromForm
   * @param request optional current HttpServletRequest request, may be null.
   * @return the persisted User
   * @throws IllegalStateException if the deployment environment does not allow a particular
   *     implementation. This should only be thrown if the wrong implementation is wired up.
   */
  User saveUser(User userFromForm, HttpServletRequest request) throws UserExistsException;
}
