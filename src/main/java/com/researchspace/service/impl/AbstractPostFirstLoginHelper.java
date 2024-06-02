package com.researchspace.service.impl;

import com.researchspace.model.User;
import com.researchspace.service.InitializedContent;
import com.researchspace.service.PostFirstLoginAction;
import javax.servlet.http.HttpSession;

/** Helper class for implementing PostFirstLoginAction */
public abstract class AbstractPostFirstLoginHelper implements PostFirstLoginAction {

  @Override
  public final void setCompleted(User user, HttpSession session, String sessionAttributeName) {
    session.setAttribute(sessionAttributeName, Boolean.TRUE);
  }

  @Override
  public final String onFirstLoginAfterContentInitialisation(
      User user, HttpSession session, InitializedContent content) {
    String redirect = null;
    if (isNotAlreadyHandled(user, session, getSessionAttributeName())) {
      redirect = doFirstLoginAfterContentInitialisation(user, session, content);
    }
    return redirect;
  }

  /**
   * Template pattern. Checks if this action has already been called before invoking <code>
   * doFirstLoginBeforeContentInitialisation</code>
   */
  @Override
  public final String onFirstLoginBeforeContentInitialisation(User user, HttpSession session) {
    String redirect = null;
    if (isNotAlreadyHandled(user, session, getSessionAttributeName())) {
      redirect = doFirstLoginBeforeContentInitialisation(user, session);
    }
    return redirect;
  }

  boolean isNotAlreadyHandled(User user, HttpSession session, String sessionAttribute) {
    return !Boolean.TRUE.equals(session.getAttribute(sessionAttribute));
  }

  /**
   * Subclasses must return the name of the session attribute used to record if the action has
   * already been invoked.
   *
   * @return
   */
  protected abstract String getSessionAttributeName();

  /**
   * Subclasses can override - this implementation is a noop, returns null.
   *
   * @param user
   * @param session
   * @param content
   * @return
   */
  protected String doFirstLoginAfterContentInitialisation(
      User user, HttpSession session, InitializedContent content) {
    return null;
  }

  /**
   * Subclasses can override - this implementation is a noop, returns null.
   *
   * @param user
   * @param session
   * @return
   */
  protected String doFirstLoginBeforeContentInitialisation(User user, HttpSession session) {
    return null;
  }
}
