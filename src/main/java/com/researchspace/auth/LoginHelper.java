package com.researchspace.auth;

import com.researchspace.model.User;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/** Central class for performing manual login operations */
public interface LoginHelper {
  /**
   * Performs postLogin operations, assuming user is already logged in, e.g. by framework.
   *
   * @param loggedIn
   * @param request
   * @return the created {@link HttpSession}
   */
  HttpSession postLogin(User loggedIn, HttpServletRequest request);

  /**
   * Logs in, then calls postLogin
   *
   * @param toLogin
   * @param originalPwd
   * @param request
   * @return the created {@link HttpSession}
   * @throws IllegalStateException if <code>toLogin</code> cannot login, i.e {@link
   *     User#isLoginDisabled()} returns true
   */
  HttpSession login(User toLogin, String originalPwd, HttpServletRequest request);
}
