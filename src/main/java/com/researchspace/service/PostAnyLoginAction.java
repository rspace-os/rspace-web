package com.researchspace.service;

import com.researchspace.model.User;
import javax.servlet.http.HttpSession;

/**
 * Handler that is invoked after each login
 *
 * <p>User will be authenticated and logged in when these methods are called.<br>
 * Therefore implementing classes should aim to not call long or slow methods.
 */
public interface PostAnyLoginAction {

  /**
   * @param user
   * @param session
   * @return String redirectURL or <code>null</code> if none required
   */
  String onAnyLogin(User user, HttpSession session);
}
