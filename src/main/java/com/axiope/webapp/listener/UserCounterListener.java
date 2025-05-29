package com.axiope.webapp.listener;

import static com.researchspace.session.UserSessionTracker.USERS_KEY;

import com.researchspace.model.User;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.session.UserSessionTracker;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingEvent;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UserCounterListener class used to count the current number of active users for the applications.
 * Does this by counting how many user objects are stuffed into the session. It also grabs these
 * users and exposes them in the servlet context.
 */
public class UserCounterListener implements ServletContextListener, HttpSessionAttributeListener {

  private Logger log = LoggerFactory.getLogger(UserCounterListener.class);
  private static final Logger SECURE_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  /** Name of user counter variable */
  public static final String COUNT_KEY = "userCounter";

  /** The default event we're looking to trap. */
  public static final String EVENT_KEY =
      "org.apache.shiro.subject.support.DefaultSubjectContext_PRINCIPALS_SESSION_KEY";

  private transient ServletContext servletContext;

  private UserSessionTracker users;

  /** Initialize the context */
  public synchronized void contextInitialized(ServletContextEvent sce) {
    log.info("Context initialised");
    servletContext = sce.getServletContext();
  }

  /** Set the servletContext, users and counter to null */
  public synchronized void contextDestroyed(ServletContextEvent event) {
    log.info("Servlet context destroyed....resetting counter.");
    servletContext = null;
    users = null;
  }

  synchronized void addUsersession(String user, HttpSession session) {
    users = (UserSessionTracker) servletContext.getAttribute(USERS_KEY);
    if (users == null) {
      users = new UserSessionTracker();
      servletContext.setAttribute(USERS_KEY, users);
    }
    users.addUser(user, session);
  }

  synchronized void removeUsersession(String username, HttpSession session) {
    users = (UserSessionTracker) servletContext.getAttribute(USERS_KEY);
    users.removeUser(username, session);
  }

  /**
   * This method catches user's login and records their name
   *
   * @param event the event to process
   * @see
   *     javax.servlet.http.HttpSessionAttributeListener#attributeAdded(javax.servlet.http.HttpSessionBindingEvent)
   */
  public void attributeAdded(HttpSessionBindingEvent event) {
    log.debug("getting notification of session binding event  {}", event.getName());
    if (event.getName().equals(SessionAttributeUtils.USER)) {
      User u = (User) event.getValue();
      addUsersession(u.getUsername(), event.getSession());
      log.info("Added {}  to active session", u.getUsername());
    }
  }

  /**
   * When user's logout, remove their name from the hashMap
   *
   * @param event the session binding event
   * @see
   *     javax.servlet.http.HttpSessionAttributeListener#attributeRemoved(javax.servlet.http.HttpSessionBindingEvent)
   */
  public void attributeRemoved(HttpSessionBindingEvent event) {
    log.debug("getting notification of session binding event  {}", event.getName());
    if (event.getName().equals(SessionAttributeUtils.USER)) {
      User u = (User) event.getValue();
      removeUsersession(u.getUsername(), event.getSession());
      log.info("Removed {} from active session.", u.getUsername());
      SECURE_LOG.info(
          "User session terminated either by timeout or explicit logout for user [{}]",
          u.getUsername());
    }
  }

  /**
   * @param event the session binding event
   * @see javax.servlet.http.HttpSessionAttributeListener#attributeReplaced
   *     (javax.servlet.http.HttpSessionBindingEvent)
   */
  public void attributeReplaced(HttpSessionBindingEvent event) {
    if (event.getName().equals(EVENT_KEY)) {
      Subject subject = SecurityUtils.getSubject();
      addUsersession((String) subject.getPrincipal(), event.getSession());
    }
  }

  public int getTotalSessions() {
    users = (UserSessionTracker) servletContext.getAttribute(USERS_KEY);
    return users == null ? 0 : users.getTotalSessions();
  }
}
