package com.axiope.webapp.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import javax.servlet.ServletContextEvent;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;

public class SessionCookieNameListenerTest {

  private final MockServletContext servletContext = new MockServletContext();
  private final SessionCookieNameListener listener = new SessionCookieNameListener();

  @AfterEach
  void clearProperty() {
    System.clearProperty(SessionCookieNameListener.SESSION_COOKIE_NAME_PROPERTY);
  }

  @Test
  void renamesSessionCookieWhenPropertyIsSet() {
    System.setProperty(SessionCookieNameListener.SESSION_COOKIE_NAME_PROPERTY, "JSESSIONID_8111");
    listener.contextInitialized(new ServletContextEvent(servletContext));
    assertEquals("JSESSIONID_8111", servletContext.getSessionCookieConfig().getName());
  }

  @Test
  void trimsConfiguredName() {
    System.setProperty(SessionCookieNameListener.SESSION_COOKIE_NAME_PROPERTY, " JSESSIONID_8111 ");
    listener.contextInitialized(new ServletContextEvent(servletContext));
    assertEquals("JSESSIONID_8111", servletContext.getSessionCookieConfig().getName());
  }

  @Test
  void leavesContainerDefaultWhenPropertyIsBlank() {
    // a blank property falls back to the env variable, which may be set in dev containers
    assumeTrue(
        StringUtils.isBlank(System.getenv(SessionCookieNameListener.SESSION_COOKIE_NAME_ENV)));
    System.setProperty(SessionCookieNameListener.SESSION_COOKIE_NAME_PROPERTY, "  ");
    listener.contextInitialized(new ServletContextEvent(servletContext));
    assertNull(servletContext.getSessionCookieConfig().getName());
  }
}
