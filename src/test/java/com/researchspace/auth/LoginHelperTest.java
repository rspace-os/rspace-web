package com.researchspace.auth;

import static com.researchspace.model.record.TestFactory.createAnyUser;
import static com.researchspace.session.SessionAttributeUtils.FIRST_LOGIN;
import static com.researchspace.session.SessionAttributeUtils.RSPACE_VERSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.model.User;
import com.researchspace.model.dtos.NotificationStatus;
import com.researchspace.model.events.UserAccountEvent;
import com.researchspace.properties.PropertyHolder;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.MessageAndNotificationTracker;
import com.researchspace.session.SessionAttributeUtils;
import java.util.Calendar;
import javax.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.mock.web.MockHttpServletRequest;

public class LoginHelperTest {

  @Rule public MockitoRule rule = MockitoJUnit.rule();
  @Mock UserManager userMgr;
  @Mock CommunicationManager commGr;
  @Mock MessageAndNotificationTracker tracker;
  @Mock AnalyticsManager analMgr;
  @Mock TimezoneAdjuster timezoneAdjuster;
  @Mock PropertyHolder properties;

  static class LoginHelperImplTSS extends ManualLoginHelperImpl {
    boolean isLoggedIn = false;

    void doLogin(User toLogin, String originalPwd) {
      isLoggedIn = true;
    }
  }

  LoginHelper loginHelper;
  @InjectMocks LoginHelperImplTSS loginHelperTSS;
  MockHttpServletRequest req = null;

  @Before
  public void setUp() throws Exception {
    loginHelper = loginHelperTSS;
    req = new MockHttpServletRequest();
  }

  @Test
  public void testPostLoginHappyCase() {
    // given
    User any = createAnyUser("any");
    assertNull(any.getLastLogin());
    any.setId(1L);
    when(commGr.getNotificationStatus(any)).thenReturn(new NotificationStatus(1, 1, 1));
    when(properties.getVersionMessage()).thenReturn("1.69.3");
    // when
    HttpSession session = loginHelper.postLogin(any, req);
    // then
    assertPostLoginAssertions(any, req, session);
    assertEquals(Boolean.TRUE, session.getAttribute(FIRST_LOGIN));
    assertEquals(Boolean.TRUE, session.getAttribute(BaseLoginHelperImpl.RECENT_SIGNUP_ATTR));

    assertEquals("1.69.3", session.getAttribute(RSPACE_VERSION));

    // when 2nd session
    HttpSession secondSession = loginHelper.postLogin(any, req);
    // then
    assertEquals(Boolean.FALSE, secondSession.getAttribute(SessionAttributeUtils.FIRST_LOGIN));
    assertEquals(Boolean.TRUE, secondSession.getAttribute(BaseLoginHelperImpl.RECENT_SIGNUP_ATTR));

    assertEquals(Boolean.TRUE, secondSession.getAttribute(BaseLoginHelperImpl.RECENT_SIGNUP_ATTR));

    // these are false for a new user
    assertEquals(
        Boolean.FALSE, secondSession.getAttribute(BaseLoginHelperImpl.AUTOSHARE_ENABLED_ATTR));
    assertEquals(Boolean.FALSE, secondSession.getAttribute(BaseLoginHelperImpl.USER_IN_GROUP_ATTR));
  }

  @Test
  public void testOldUserPostLoginHappyCase() {
    User user = Mockito.spy(createAnyUser("any"));
    user.setId(1L);
    Mockito.when(commGr.getNotificationStatus(user)).thenReturn(new NotificationStatus(1, 1, 1));

    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.DAY_OF_MONTH, -1);
    Mockito.when(user.getCreationDate()).thenReturn(cal.getTime());

    HttpSession recentUserSession = loginHelper.postLogin(user, req);
    assertEquals(Boolean.TRUE, recentUserSession.getAttribute(SessionAttributeUtils.FIRST_LOGIN));
    assertEquals(
        Boolean.TRUE, recentUserSession.getAttribute(BaseLoginHelperImpl.RECENT_SIGNUP_ATTR));

    HttpSession recentUserSecondSession = loginHelper.postLogin(user, req);
    assertEquals(
        Boolean.FALSE, recentUserSecondSession.getAttribute(SessionAttributeUtils.FIRST_LOGIN));
    assertEquals(
        Boolean.TRUE, recentUserSecondSession.getAttribute(BaseLoginHelperImpl.RECENT_SIGNUP_ATTR));

    cal.add(Calendar.YEAR, -1);
    Mockito.when(user.getCreationDate()).thenReturn(cal.getTime());
    user.setLastLogin(null);
    HttpSession oldUserFirstSession = loginHelper.postLogin(user, req);
    assertEquals(Boolean.TRUE, oldUserFirstSession.getAttribute(SessionAttributeUtils.FIRST_LOGIN));
    assertEquals(
        Boolean.FALSE, oldUserFirstSession.getAttribute(BaseLoginHelperImpl.RECENT_SIGNUP_ATTR));
  }

  @Test
  public void firstLoginHappyCase() {
    User any = createAnyUser("any");

    assertNull(any.getLastLogin());
    any.setId(1L);
    Mockito.when(commGr.getNotificationStatus(any)).thenReturn(new NotificationStatus(1, 1, 1));
    HttpSession session = loginHelper.login(any, any.getPassword(), req);
    assertTrue(loginHelperTSS.isLoggedIn);
    assertPostLoginAssertions(any, req, session);
    assertEquals(Boolean.TRUE, session.getAttribute(SessionAttributeUtils.FIRST_LOGIN));
  }

  @Test(expected = IllegalStateException.class)
  public void testLoginThrowsISEIfAccountDisabled() {
    User any = createAnyUser("any");
    any.setEnabled(false);
    loginHelper.login(any, any.getPassword(), req);
  }

  private void assertPostLoginAssertions(
      User any, MockHttpServletRequest req, HttpSession session) {
    assertNotNull(session);
    assertNotNull(any.getLastLogin());
    assertNull(any.getLoginFailure());

    verify(analMgr).userLoggedIn(any, req);
    verify(timezoneAdjuster).setUserTimezoneInSession(req, session);
    verify(tracker).initCount(Mockito.anyLong(), Mockito.any(NotificationStatus.class));
    verify(userMgr).saveUserAccountEvent(Mockito.any(UserAccountEvent.class));
  }
}
