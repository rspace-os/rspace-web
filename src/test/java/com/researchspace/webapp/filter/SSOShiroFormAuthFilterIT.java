package com.researchspace.webapp.filter;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.webapp.filter.RemoteUserRetrievalPolicy.SSO_DUMMY_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.auth.AccountEnabledAuthorizer;
import com.researchspace.auth.LoginAuthorizer;
import com.researchspace.auth.LoginHelper;
import com.researchspace.auth.MaintenanceLoginAuthorizer;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.properties.IMutablePropertyHolder;
import com.researchspace.service.UserExistsException;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import com.researchspace.testutils.TestFactory;
import com.researchspace.webapp.controller.SignupController;
import com.researchspace.webapp.controller.WorkspaceController;
import org.apache.shiro.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Login bookkeeping commits in its own transaction, so test accounts must be committed. */
public class SSOShiroFormAuthFilterIT extends RealTransactionSpringTestBase {

  private SSOShiroFormAuthFilterExt filter;

  private MockHttpServletRequest req;
  private MockHttpServletResponse resp;

  private @Autowired MockRemoteUserPolicy remoteUserPolicy;
  private @Autowired IMutablePropertyHolder propertyHolder;

  @Autowired
  @Qualifier("accountEnabledAuthorizer")
  private LoginAuthorizer accountEnabledAuthorizer;

  @Autowired private MaintenanceLoginAuthorizer maintenanceLoginAuthorizer;

  @Autowired
  @Qualifier("manualLoginHelper")
  private LoginHelper loginHelper;

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
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

  @AfterEach
  @Override
  public void tearDown() throws Exception {
    RSpaceTestUtils.logout();
    super.tearDown();
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
    user.setUsernameAlias(getRandomName(10));
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
    assertThat(resp.getHeaderValue("Location")).hasToString(SSOShiroFormAuthFilterExt.SSOINFO_URL);
    assertTrue(hasRemoteUserUsernameSet());

    propertyHolder.setUserSignup("true");
    resp = new MockHttpServletResponse();
    assertFalse(filter.isAccessAllowed(req, resp, null));
    assertThat(resp.getHeaderValue("Location")).hasToString(SignupController.SIGNUP_URL);
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
        WorkspaceController.ROOT_URL,
        resp.getHeaderValue("Location"),
        "should be redirected to workspace, but location was " + resp.getHeaderValue("Location"));
  }

  @Test
  public void ssoUserIsRedirectedFromMaintenanceLoginPage() throws UserExistsException {
    User u = createAndSaveSsoTestUser();
    remoteUserPolicy.setUsername(u.getUsername());
    req.setRequestURI("/login?maintenanceLogin");
    assertTrue(filter.isAccessAllowed(req, resp, null));
    assertEquals(
        WorkspaceController.ROOT_URL,
        resp.getHeaderValue("Location"),
        "should be redirected to workspace, but location was " + resp.getHeaderValue("Location"));
  }

  @Test
  public void ssoRemoteUsernameMatchingUserWhoAlsoHasAnAliasRedirectedToErrorPage()
      throws UserExistsException {
    User u = createAndSaveSsoTestUser();
    u.setUsernameAlias(getRandomName(10));
    userMgr.saveUser(u);

    remoteUserPolicy.setUsername(u.getUsername());
    req.setRequestURI("/login");
    assertFalse(filter.isAccessAllowed(req, resp, null));
    assertEquals(
        SSOShiroFormAuthFilterExt.SSOINFO_USERNAMENOTALIAS_URL,
        resp.getHeaderValue("Location"),
        "sso username matching username of user with alias should redirected to info page");

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
    userMgr.saveUser(u);
    remoteUserPolicy.setUsername(u.getUsername());
    req.setRequestURI("/login");
    assertFalse(filter.isAccessAllowed(req, resp, null));
    assertEquals(
        SSOShiroFormAuthFilterExt.SSOINFO_USERNAMECONFLICT_URL,
        resp.getHeaderValue("Location"),
        "sso username matching internal user should be redirected to username conflict page");
  }

  @Test
  public void ssoRemoteUserWithIsAllowedPiRoleProperty_RSPAC2588() throws UserExistsException {
    propertyHolder.setSSOSelfDeclarePiEnabled(Boolean.TRUE);

    // create and login user without isAllowedPiRole
    User u = createAndSaveSsoTestUser();
    assertFalse(u.isAllowedPiRole());
    remoteUserPolicy.setUsername(u.getUsername());
    assertTrue(filter.isAccessAllowed(req, resp, null));
    assertFalse(userMgr.get(u.getId()).isAllowedPiRole());

    // logout, set isAllowePiRole property to true, login again
    SecurityUtils.getSubject().logout();
    remoteUserPolicy.setIsAllowedPiRole("true");
    assertTrue(filter.isAccessAllowed(req, resp, null));
    assertTrue(userMgr.get(u.getId()).isAllowedPiRole());

    // logout, set isAllowePiRole property to false, login again
    SecurityUtils.getSubject().logout();
    remoteUserPolicy.setIsAllowedPiRole("false");
    assertTrue(filter.isAccessAllowed(req, resp, null));
    assertFalse(userMgr.get(u.getId()).isAllowedPiRole());
  }

  private User createAndSaveSsoTestUser() throws UserExistsException {
    User u = TestFactory.createAnyUser(getRandomName(10));
    u.setEmail(u.getUsername() + "@rs.com");
    u.setPassword(SSO_DUMMY_PASSWORD);
    u = userMgr.saveNewUser(u);
    return u;
  }
}
