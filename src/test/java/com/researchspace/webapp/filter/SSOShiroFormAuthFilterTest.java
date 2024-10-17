package com.researchspace.webapp.filter;

import static com.researchspace.webapp.filter.RemoteUserRetrievalPolicy.SSO_DUMMY_PASSWORD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.auth.AccountEnabledAuthorizer;
import com.researchspace.auth.LoginAuthorizer;
import com.researchspace.auth.LoginHelper;
import com.researchspace.auth.MaintenanceLoginAuthorizer;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.UserExistsException;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.controller.SignupController;
import com.researchspace.webapp.controller.WorkspaceController;
import org.apache.shiro.SecurityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class SSOShiroFormAuthFilterTest extends SpringTransactionalTest {

  private SSOShiroFormAuthFilterExt filter;

  private MockHttpServletRequest req;
  private MockHttpServletResponse resp;

  private @Autowired MockRemoteUserPolicy remoteUserPolicy;

  @Autowired
  @Qualifier("accountEnabledAuthorizer")
  private LoginAuthorizer accountEnabledAuthorizer;

  @Autowired private MaintenanceLoginAuthorizer maintenanceLoginAuthorizer;

  @Autowired
  @Qualifier("manualLoginHelper")
  private LoginHelper loginHelper;

  @Before
  public void setUp() {
    this.remoteUserPolicy = new MockRemoteUserPolicy();
    filter = new SSOShiroFormAuthFilterExt(remoteUserPolicy);
    filter.setUserMgr(userMgr);
    filter.setMaintenanceLoginAuthorizer(maintenanceLoginAuthorizer);
    filter.setLoginHelper(loginHelper);
    filter.setProperties(propertyHolder);
    filter.setAccountEnabledAuthorizer(accountEnabledAuthorizer);
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
  }

  @After
  public void tearDown() {
    RSpaceTestUtils.logout();
  }

  @Test
  public void testExistingUserAccessAllowed() throws UserExistsException {
    propertyHolder.setUserSignup("false");

    User user = createAndSaveSsoTestUser();
    assertFalse(hasRemoteUserUsernameSet());
    remoteUserPolicy.setUsername(user.getUsername());
    assertTrue(filter.isAccessAllowed(req, resp, null));
    assertTrue(hasRemoteUserUsernameSet());

    // can login using usernameAlias
    user.setUsernameAlias("testAlias");
    userMgr.saveUser(user);
    logoutAndResetMockRequestResponse();
    remoteUserPolicy.setUsername(user.getUsernameAlias());
    assertTrue(filter.isAccessAllowed(req, resp, null));
    assertTrue(hasRemoteUserUsernameSet());

    // confirm access rejected for unknown login/alias
    logoutAndResetMockRequestResponse();
    remoteUserPolicy.setUsername(user.getUsername() + "unknown");
    assertFalse(filter.isAccessAllowed(req, resp, null));
    assertTrue(hasRemoteUserUsernameSet());
  }

  private void logoutAndResetMockRequestResponse() {
    SecurityUtils.getSubject().logout();
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
  }

  private boolean hasRemoteUserUsernameSet() {
    return req.getSession().getAttribute(SSOShiroFormAuthFilterExt.REMOTE_USER_USERNAME_ATTR)
        != null;
  }

  @Test
  public void testUnknownUserRedirectsWithUserSignupDisabled() {
    remoteUserPolicy.setUsername("unknown@rs.com");
    assertFalse(hasRemoteUserUsernameSet());

    propertyHolder.setUserSignup("false");
    assertFalse(filter.isAccessAllowed(req, resp, null));
    assertEquals(SSOShiroFormAuthFilterExt.SSOINFO_URL, resp.getHeaderValue("Location").toString());
    assertTrue(hasRemoteUserUsernameSet());

    propertyHolder.setUserSignup("true");
    resp = new MockHttpServletResponse();
    assertFalse(filter.isAccessAllowed(req, resp, null));
    assertEquals(SignupController.SIGNUP_URL, resp.getHeaderValue("Location").toString());
  }

  @Test
  public void testDisabledUserLoggedOutAndRedirected() throws UserExistsException {
    User user = createAndSaveSsoTestUser();
    user.setEnabled(false);
    userMgr.saveUser(user);

    remoteUserPolicy.setUsername(user.getUsername());
    assertFalse(filter.isAccessAllowed(req, resp, null));
    assertEquals(AccountEnabledAuthorizer.REDIRECT_FOR_DISABLED, resp.getHeaderValue("Location"));
  }

  @Test
  public void testOnAccessDenied() throws Exception {
    assertFalse(filter.onAccessDenied(req, resp));
  }

  @Test
  public void ssoUserIsRedirectedFromLoginPage() throws UserExistsException {
    User u = createAndSaveSsoTestUser();
    remoteUserPolicy.setUsername(u.getUsername());
    req.setRequestURI("/login");
    assertTrue(filter.isAccessAllowed(req, resp, null));
    assertEquals(
        "should be redirected to workspace, but location was " + resp.getHeaderValue("Location"),
        WorkspaceController.ROOT_URL,
        resp.getHeaderValue("Location"));
  }

  @Test
  public void ssoUserIsRedirectedFromMaintenanceLoginPage() throws UserExistsException {
    User u = createAndSaveSsoTestUser();
    remoteUserPolicy.setUsername(u.getUsername());
    req.setRequestURI("/login?maintenanceLogin");
    assertTrue(filter.isAccessAllowed(req, resp, null));
    assertEquals(
        "should be redirected to workspace, but location was " + resp.getHeaderValue("Location"),
        WorkspaceController.ROOT_URL,
        resp.getHeaderValue("Location"));
  }

  @Test
  public void ssoRemoteUsernameMatchingUserWhoAlsoHasAnAliasRedirectedToErrorPage()
      throws UserExistsException {
    User u = createAndSaveSsoTestUser();
    u.setUsernameAlias("testAlias");
    userMgr.saveUser(u);

    remoteUserPolicy.setUsername(u.getUsername());
    req.setRequestURI("/login");
    assertFalse(filter.isAccessAllowed(req, resp, null));
    assertEquals(
        "sso username matching username of user with alias should redirected to info page",
        SSOShiroFormAuthFilterExt.SSOINFO_USERNAMENOTALIAS_URL,
        resp.getHeaderValue("Location"));

    // confirm that user can login with alias
    logoutAndResetMockRequestResponse();
    remoteUserPolicy.setUsername(u.getUsernameAlias());
    assertTrue(filter.isAccessAllowed(req, resp, null));
  }

  @Test
  public void ssoRemoteUsernameMatchingBackdoorUsernameRedirectedToConflictPage()
      throws UserExistsException {
    User u = createAndSaveSsoTestUser();
    u.setSignupSource(SignupSource.SSO_BACKDOOR);
    remoteUserPolicy.setUsername(u.getUsername());
    req.setRequestURI("/login");
    assertFalse(filter.isAccessAllowed(req, resp, null));
    assertEquals(
        "sso username matching internal user should be redirected to username conflict page",
        SSOShiroFormAuthFilterExt.SSOINFO_USERNAMECONFLICT_URL,
        resp.getHeaderValue("Location"));
  }

  @Test
  public void ssoRemoteUserWithIsAllowedPiRoleProperty_RSPAC2588() throws UserExistsException {
    propertyHolder.setSSOSelfDeclarePiEnabled(Boolean.TRUE);

    // create and login user without isAllowedPiRole
    User u = createAndSaveSsoTestUser();
    assertFalse(u.isAllowedPiRole());
    remoteUserPolicy.setUsername(u.getUsername());
    assertTrue(filter.isAccessAllowed(req, resp, null));
    assertFalse(u.isAllowedPiRole());

    // logout, set isAllowePiRole property to true, login again
    SecurityUtils.getSubject().logout();
    remoteUserPolicy.setIsAllowedPiRole("true");
    assertTrue(filter.isAccessAllowed(req, resp, null));
    assertTrue(u.isAllowedPiRole());

    // logout, set isAllowePiRole property to false, login again
    SecurityUtils.getSubject().logout();
    remoteUserPolicy.setIsAllowedPiRole("false");
    assertTrue(filter.isAccessAllowed(req, resp, null));
    assertFalse(u.isAllowedPiRole());
  }

  private User createAndSaveSsoTestUser() throws UserExistsException {
    User u = TestFactory.createAnyUser("ssoTestUser");
    u.setEmail(u.getUsername() + "@rs.com");
    u.setPassword(SSO_DUMMY_PASSWORD);
    u = userMgr.saveNewUser(u);
    return u;
  }

}
