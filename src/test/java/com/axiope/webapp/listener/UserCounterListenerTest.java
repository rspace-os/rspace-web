package com.axiope.webapp.listener;

import static com.researchspace.session.UserSessionTracker.USERS_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.session.UserSessionTracker;
import com.researchspace.testutils.SpringTransactionalTest;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;

public class UserCounterListenerTest extends SpringTransactionalTest {

  private MockServletContext mockServletContext = null;
  private ServletContext servletContext = null;
  private UserCounterListener listener = null;
  private HttpSession mockSession = new MockHttpSession();

  @Before
  public void setUp() throws Exception {
    mockServletContext = new MockServletContext();
    servletContext = mockServletContext;
    listener = new UserCounterListener();
    ServletContextEvent e = new ServletContextEvent(mockServletContext);
    listener.contextInitialized(e);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void contextInitialized() {
    assertEquals(0, listener.getTotalSessions());
  }

  @Test
  public void contextDestroyed() {
    ServletContextEvent e = new ServletContextEvent(mockServletContext);
    listener.contextDestroyed(e);
    assertNull(getUsers());
  }

  private UserSessionTracker getUsers() {
    return (UserSessionTracker) servletContext.getAttribute(USERS_KEY);
  }

  @Test
  public void attributeAddedRemoved() {
    User u1 = TestFactory.createAnyUser("u1");
    User u2 = TestFactory.createAnyUser("u2");
    HttpSessionBindingEvent event =
        new HttpSessionBindingEvent(mockSession, SessionAttributeUtils.USER, u1);
    listener.attributeAdded(event);
    assertEquals(1, getUsers().getTotalActiveUsers());
    assertEquals(1, listener.getTotalSessions());

    HttpSessionBindingEvent event2 =
        new HttpSessionBindingEvent(mockSession, SessionAttributeUtils.USER, u2);
    listener.attributeAdded(event2);

    assertEquals(2, getUsers().getTotalActiveUsers());
    assertEquals(2, listener.getTotalSessions());
    // add same user in different session
    HttpSession mockSession2 = new MockHttpSession();
    HttpSessionBindingEvent event3 =
        new HttpSessionBindingEvent(mockSession2, SessionAttributeUtils.USER, u2);
    listener.attributeAdded(event3);

    assertEquals(2, getUsers().getTotalActiveUsers());
    assertEquals(3, listener.getTotalSessions());
    // u2 is logged in to second session still...
    listener.attributeRemoved(event2); // remove u2, session 1

    assertEquals(2, getUsers().getTotalActiveUsers());
    assertEquals(2, listener.getTotalSessions());
    // calling again now just leaves u1
    listener.attributeRemoved(event3); // remove u2, session 2

    assertEquals(1, getUsers().getTotalActiveUsers());
    assertEquals(1, listener.getTotalSessions());
    // multiple deletes handled even if empty
    for (int i = 0; i < 2; i++) {
      listener.attributeRemoved(event); // noone eleft

      assertEquals(0, getUsers().getTotalActiveUsers());
      assertEquals(0, listener.getTotalSessions());
    }
  }
}
