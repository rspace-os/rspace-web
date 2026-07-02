package com.axiope.webapp.listener;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optionally renames the HTTP session cookie (container default: {@code JSESSIONID}) before the
 * servlet context finishes initialising.
 *
 * <p>Browsers scope cookies by host only, not port, so several RSpace instances running on
 * different localhost ports (e.g. one Dockerized dev stack per git worktree) would otherwise share
 * a single {@code JSESSIONID} cookie and log each other out. Giving each instance a unique cookie
 * name keeps their sessions independent.
 *
 * <p>The name is read from the {@value #SESSION_COOKIE_NAME_PROPERTY} system property, falling back
 * to the {@value #SESSION_COOKIE_NAME_ENV} environment variable. When neither is set the container
 * default is left untouched, so production deployments are unaffected.
 */
public class SessionCookieNameListener implements ServletContextListener {

  /** System property holding the session cookie name; takes precedence over the env variable. */
  public static final String SESSION_COOKIE_NAME_PROPERTY = "rs.session.cookie.name";

  /** Environment variable consulted when the system property is not set. */
  public static final String SESSION_COOKIE_NAME_ENV = "RSPACE_SESSION_COOKIE_NAME";

  private static final Logger log = LoggerFactory.getLogger(SessionCookieNameListener.class);

  @Override
  public void contextInitialized(ServletContextEvent event) {
    String name = getConfiguredName();
    if (StringUtils.isNotBlank(name)) {
      event.getServletContext().getSessionCookieConfig().setName(name.trim());
      log.info("Session cookie name set to '{}'", name.trim());
    }
  }

  private String getConfiguredName() {
    String name = System.getProperty(SESSION_COOKIE_NAME_PROPERTY);
    return StringUtils.isNotBlank(name) ? name : System.getenv(SESSION_COOKIE_NAME_ENV);
  }

  @Override
  public void contextDestroyed(ServletContextEvent event) {}
}
