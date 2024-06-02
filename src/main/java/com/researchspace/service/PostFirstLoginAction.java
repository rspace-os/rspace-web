package com.researchspace.service;

import com.researchspace.model.User;
import javax.servlet.http.HttpSession;

/**
 * Handler that is invoked on 1st login. Methods have default no-op implementation returning null.
 * <br>
 * Subclasses can implement any or all methods. <br>
 * User will be authenticated and logged in when these methods are called.
 *
 * <h3>Implementation notes </h3>
 *
 * Implementors can extend <code>AbstractPostFirstLoginHelper</code> which provides support methods.
 * Implementation classes should hook into either onFirstLoginBeforeContentInitialisation or
 * onFirstLoginAfterContentInitialisation, but not both, and call setCompleted when invocation has
 * finished to ensure method is not called more than once.
 */
public interface PostFirstLoginAction {

  /**
   * Invoked before contentInitialisor is called
   *
   * @param user
   * @param session
   * @return A redirect URL if login should redirect to another URL
   */
  default String onFirstLoginBeforeContentInitialisation(User user, HttpSession session) {
    return null;
  }

  /**
   * Invoked after contentInitialisor is called
   *
   * @param user
   * @param session
   * @param initializedContent
   * @return A redirect URL if login should redirect to another URL
   */
  default String onFirstLoginAfterContentInitialisation(
      User user, HttpSession session, InitializedContent initializedContent) {
    return null;
  }

  /**
   * Sets a session attribute that this initialisatioon action is completed.
   *
   * @param user
   * @param session
   * @param sessionAttributeName
   */
  public void setCompleted(User user, HttpSession session, String sessionAttributeName);
}
