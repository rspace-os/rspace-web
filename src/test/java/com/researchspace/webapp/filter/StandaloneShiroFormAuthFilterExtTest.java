package com.researchspace.webapp.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.researchspace.auth.AccountEnabledAuthorizer;
import com.researchspace.auth.LoginAuthorizer;
import com.researchspace.auth.LoginHelper;
import com.researchspace.auth.MaintenanceLoginAuthorizer;
import com.researchspace.auth.SidVerificationException;
import com.researchspace.core.util.RequestUtil;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserSignupException;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Date;
import java.util.List;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class StandaloneShiroFormAuthFilterExtTest extends SpringTransactionalTest {

  private StandaloneShiroFormAuthFilterExt filter;

  private MockHttpServletRequest req;
  private MockHttpServletResponse resp;

  private DefaultLockoutPolicy policy = new DefaultLockoutPolicy();
  @Mock private IPropertyHolder mockProperties;

  @Autowired
  @Qualifier("manualLoginHelper")
  private LoginHelper loginHelper;

  @Autowired private List<LoginAuthorizer> loginauths;

  @Before
  public void setUp() {
    openMocks(this);
    filter = new StandaloneShiroFormAuthFilterExt();
    filter.setUserMgr(userMgr);
    filter.setLoginHelper(loginHelper);
    filter.setLockoutPolicy(policy);
    filter.setMessages(messages);
    filter.setLoginAuthorizers(loginauths);
    filter.setProperties(propertyHolder);
    initHttpReqAndResp(null);
  }

  // this needs to be called each time we make a request as response can only be used once.
  private void initHttpReqAndResp(User u) {
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
    if (u != null) {
      req.setParameter("username", u.getUsername());
    }
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testOnAccessDeniedLdapAuthUserSignUpEnabled() throws Exception {

    configureHttpReqAsLoginRequest();
    assertNull(getShiroLoginFailureAttr(req));
    filter.setProperties(mockProperties);
    when(mockProperties.isUserSignup()).thenReturn(true);
    when(mockProperties.isLdapAuthenticationEnabled()).thenReturn(true);
    String userName = getRandomAlphabeticString("newUser");
    req.setParameter("username", userName);
    boolean allowed = filter.onAccessDenied(req, resp);
    assertNull(getShiroLoginFailureAttr(req));
    assertTrue(allowed);
  }

  @Test
  public void testOnAccessDeniedLdapAuthUserSignUpNotEnabled() throws Exception {

    configureHttpReqAsLoginRequest();
    assertNull(getShiroLoginFailureAttr(req));
    filter.setProperties(mockProperties);
    when(mockProperties.isUserSignup()).thenReturn(false);
    when(mockProperties.isLdapAuthenticationEnabled()).thenReturn(true);
    String userName = getRandomAlphabeticString("newUser");
    req.setParameter("username", userName);
    filter.onAccessDenied(req, resp);
    String redirect = getRedirectUrl();
    assertEquals("/public/noldapsignup", redirect);
  }

  @Test
  public void testOnLoginSuccessAuthenticationToken() throws Exception {

    User u = createAndSaveRandomUser();
    RSpaceTestUtils.login(u.getUsername(), TESTPASSWD);
    Subject subject = SecurityUtils.getSubject();
    Date b4 = new Date();
    assertFalse(filter.onLoginSuccess(getTokenAndSetUsernameInRequest(u), subject, req, resp));

    u = userMgr.get(u.getId());
    // last login is set, and subject remains authenticated.
    assertNotNull(u.getLastLogin());
    assertFalse(u.getLastLogin().before(b4)); // check is updated
    assertTrue(subject.isAuthenticated());
  }

  @Test
  public void onLoginFailureBehaviour() throws Exception {

    User u = createAndSaveRandomUser();
    UsernamePasswordToken token = getTokenAndSetUsernameInRequest(u);
    AuthenticationException authException;

    // auto-signup problem
    UserSignupException userSignupException = new UserSignupException("signup exception");
    authException = new AuthenticationException(null, userSignupException);
    filter.onLoginFailure(token, authException, req, resp);
    assertEquals(userSignupException.getMessage(), req.getAttribute("checkedExceptionMessage"));
    // shouldn't increase login failures count
    u = userMgr.get(u.getId());
    assertEquals(0, u.getNumConsecutiveLoginFailures());

    // sid verification problem
    initHttpReqAndResp(u);
    SidVerificationException sidException =
        new SidVerificationException("sid verification exception");
    authException = new AuthenticationException(null, sidException);
    filter.onLoginFailure(token, authException, req, resp);
    assertEquals(sidException.getMessage(), req.getAttribute("checkedExceptionMessage"));
    // shouldn't increase login failures count
    u = userMgr.get(u.getId());
    assertEquals(0, u.getNumConsecutiveLoginFailures());

    // wrong password problem
    initHttpReqAndResp(u);
    authException = new AuthenticationException("wrong password");
    filter.onLoginFailure(token, authException, req, resp);
    assertEquals(null, req.getAttribute("checkedExceptionMessage"));
    // wrong password should increase login failures count
    u = userMgr.get(u.getId());
    assertEquals(1, u.getNumConsecutiveLoginFailures());
  }

  @Test
  public void testBlockedUserRedirectedToBlockedPage() throws Exception {
    User u = createAndSaveRandomUser();
    u.setAccountLocked(true);
    userMgr.save(u);

    RSpaceTestUtils.login(u.getUsername(), TESTPASSWD);
    Subject subject = SecurityUtils.getSubject();

    boolean loginResult =
        filter.onLoginSuccess(getTokenAndSetUsernameInRequest(u), subject, req, resp);

    assertFalse(loginResult);
    assertTrue(getRedirectUrl().contains(BaseShiroFormAuthFilterExt.REDIRECT_FOR_BLOCKED));
    assertFalse(subject.isAuthenticated());
  }

  private UsernamePasswordToken getTokenAndSetUsernameInRequest(User u) {
    req.setParameter("username", u.getUsername());
    return new UsernamePasswordToken(u.getUsername(), TESTPASSWD);
  }

  @Test
  public void testTemporarilyLockedUserCantLogIn() throws Exception {
    configureHttpReqAsLoginRequest();
    assertNull(getShiroLoginFailureAttr(req));

    User u = createAndSaveRandomUser();
    req.setParameter("username", u.getUsername());
    filter.onAccessDenied(req, resp);
    assertNull(getShiroLoginFailureAttr(req));

    u.setAccountLocked(true);
    u.setLoginFailure(new Date());
    u.setNumConsecutiveLoginFailures((byte) policy.getMaxFailures());
    userMgr.save(u);

    initHttpReqAndResp(u);
    configureHttpReqAsLoginRequest();
    filter.onAccessDenied(req, resp);
    assertNotNull(getShiroLoginFailureAttr(req));
  }

  private void configureHttpReqAsLoginRequest() {
    req.setServletPath("/login.jsp");
  }

  private Object getShiroLoginFailureAttr(MockHttpServletRequest requestToCheck) {
    return requestToCheck.getAttribute("shiroLoginFailure");
  }

  @Test
  public void testDisabledUserISRedirectedCorrectly() throws Exception {
    User u = createAndSaveRandomUser();
    u.setEnabled(false);
    userMgr.save(u);

    RSpaceTestUtils.login(u.getUsername(), TESTPASSWD);
    Subject subject = SecurityUtils.getSubject();

    assertFalse(filter.onLoginSuccess(getTokenAndSetUsernameInRequest(u), subject, req, resp));
    assertTrue(getRedirectUrl().contains(AccountEnabledAuthorizer.REDIRECT_FOR_DISABLED));
    assertFalse(subject.isAuthenticated());

    // mimic login - even though account is blocked, they still need to
    // be authenticated to perform further checks
    RSpaceTestUtils.login(u.getUsername(), TESTPASSWD);
    // sanity check
    assertTrue(subject.isAuthenticated());
    initHttpReqAndResp(u);
    // also if is initialised, if disabled fails too
    initialiseContentWithEmptyContent(u);
    assertFalse(filter.onLoginSuccess(getTokenAndSetUsernameInRequest(u), subject, req, resp));
    assertTrue(getRedirectUrl().contains(AccountEnabledAuthorizer.REDIRECT_FOR_DISABLED));
    // should be logged out when redirected
    assertFalse(subject.isAuthenticated());
  }

  @Test
  public void testOnAccessDeniedFromAjaxRequest() throws Exception {
    req.addHeader(RequestUtil.AJAX_REQUEST_HEADER_NAME, RequestUtil.AJAX_REQUEST_TYPE);
    req.setLocalAddr("");
    assertFalse(filter.onAccessDenied(req, resp));
    assertEquals(getMsgFromResourceBundler("ajax.unauthenticated.msg"), resp.getContentAsString());
    assertTrue(resp.getStatus() == HttpStatus.FORBIDDEN.value());
  }

  @Test
  public void testOnAccessDeniedFromNormalRequest() throws Exception {
    req.setLocalAddr("");
    assertFalse(filter.onAccessDenied(req, resp));
    assertEquals(
        "/login.jsp",
        resp.getHeaderValue("Location"),
        "non-ajax request should be redirected to login page");
  }

  @Test
  public void testOnAccessDeniedFromAdminLoginRequest() throws Exception {
    req.setLocalAddr("");
    req.setParameter(BaseShiroFormAuthFilterExt.ADMIN_LOGIN_REQUEST_PARAM, "");
    assertFalse(filter.onAccessDenied(req, resp));
    assertEquals(
        "/adminLogin",
        resp.getHeaderValue("Location"),
        "non-ajax request should be redirected to login page");
  }

  @Test
  public void testOnAccessDeniedDontRedirectToLoginIfAlreadyRedirected() throws Exception {
    req.setLocalAddr("");
    resp.setHeader("Location", MaintenanceLoginAuthorizer.REDIRECT_FOR_MAINTENANCE);

    assertFalse(filter.onAccessDenied(req, resp));
    assertEquals(
        MaintenanceLoginAuthorizer.REDIRECT_FOR_MAINTENANCE,
        resp.getHeaderValue("Location"),
        "if response is already redirected don't redirect back to login page");
  }

  private String getRedirectUrl() {
    return resp.getHeaderValue("Location").toString();
  }
}
