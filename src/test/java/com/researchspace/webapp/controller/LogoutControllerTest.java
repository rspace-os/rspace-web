package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.Constants;
import com.researchspace.auth.ShiroRealm;
import com.researchspace.model.User;
import com.researchspace.model.dto.UserPublicInfo;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.session.UserSessionTracker;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.filter.SSOShiroFormAuthFilterExt;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.servlet.ModelAndView;

public class LogoutControllerTest extends SpringTransactionalTest {

  @Autowired private LogoutController logoutController;

  private HttpServletRequest request;

  @Before
  public void setUp() {
    request = new MockHttpServletRequest();
    MockServletContext mockServletCtxt = new MockServletContext();
    mockServletCtxt.setAttribute(UserSessionTracker.USERS_KEY, anySessionTracker());
    logoutController.setServletContext(mockServletCtxt);
  }

  @Test
  public void testStandaloneLogout() throws IOException {
    User user = createAndSaveUserIfNotExists("newuser23");
    // do regular login
    logoutAndLoginAs(user);
    assertTrue(SecurityUtils.getSubject().isAuthenticated());

    ModelAndView mav = logoutController.logout(new MockPrincipal(user.getUsername()), request);
    assertEquals("/workspace", mav.getModel().get("redirectLocation"));

    // user is logged out
    assertFalse(SecurityUtils.getSubject().isAuthenticated());
  }

  @Test
  public void testSSOLogout() throws IOException {
    User user = createAndSaveUserIfNotExists("newuser23");

    // now lets simulate SSO login
    logoutAndLoginAs(user);
    request
        .getSession()
        .setAttribute(SSOShiroFormAuthFilterExt.REMOTE_USER_USERNAME_ATTR, user.getUsername());

    ModelAndView mav = logoutController.logout(new MockPrincipal(user.getUsername()), request);
    assertEquals("/public/ssologout", mav.getModel().get("redirectLocation"));

    // user still logged in
    assertTrue(!SecurityUtils.getSubject().isAuthenticated());
  }

  @Test
  public void testRunAsRelease() {
    User otherUser = createAndSaveUserIfNotExists("newUser");
    logoutAndLoginAsSysAdmin();

    assertTrue(SecurityUtils.getSubject().hasRole(Constants.SYSADMIN_ROLE));
    runAsUser(otherUser);
    Subject subj = SecurityUtils.getSubject();
    assertFalse(subj.hasRole(Constants.SYSADMIN_ROLE));
    assertTrue(subj.hasRole(Constants.USER_ROLE));
    assertTrue(subj.isRunAs());

    // now release
    MockHttpSession mockHttpSession = new MockHttpSession();
    mockHttpSession.setAttribute(SessionAttributeUtils.IS_RUN_AS, Boolean.TRUE);
    logoutController.runAsRelease(new MockPrincipal(otherUser.getUsername()), mockHttpSession);
    Subject original = SecurityUtils.getSubject();
    assertTrue(original.hasRole(Constants.SYSADMIN_ROLE));
    // RSPAC-1018
    UserPublicInfo info =
        (UserPublicInfo) mockHttpSession.getAttribute(SessionAttributeUtils.USER_INFO);
    assertEquals(SYS_ADMIN_UNAME, info.getUsername());
  }

  private void runAsUser(User otherUser) {
    SimplePrincipalCollection pc = new SimplePrincipalCollection();
    pc.add(otherUser.getUsername(), ShiroRealm.DEFAULT_USER_PASSWD_REALM);
    SecurityUtils.getSubject().runAs(pc);
  }
}
