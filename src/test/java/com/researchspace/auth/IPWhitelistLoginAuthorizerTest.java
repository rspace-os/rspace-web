package com.researchspace.auth;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.maintenance.model.WhiteListedSysAdminIPAddress;
import com.researchspace.maintenance.service.WhiteListedIPAddressManager;
import com.researchspace.model.User;
import com.researchspace.properties.PropertyHolder;
import com.researchspace.testutils.SpringTransactionalTest;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class IPWhitelistLoginAuthorizerTest extends SpringTransactionalTest {

  @Autowired
  @Qualifier("ipWhitelistAuthorizer")
  private LoginAuthorizer ipAuth;

  private @Autowired WhiteListedIPAddressManager ipMgr;
  private MockHttpServletRequest req;
  private MockHttpServletResponse resp;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    initHttpReqAndResp();
  }

  private void initHttpReqAndResp() {
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void sysAdminWhiteListBlocksAccess() throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    Subject subject = SecurityUtils.getSubject();
    getBeanOfClass(PropertyHolder.class).setWhiteListedIpsEnabled("false");
    assertLoginSuccessful(sysadmin, subject);
    initHttpReqAndResp();

    // now we'll enable whitelist checking, and check against a valid whitelist
    getBeanOfClass(PropertyHolder.class).setWhiteListedIpsEnabled("true");
    // filter.setWhiteListedIpsEnabled(true);
    String ALLOWED_IP = RandomStringUtils.randomNumeric(5);
    ipMgr.save(new WhiteListedSysAdminIPAddress(ALLOWED_IP));
    req.setRemoteAddr(ALLOWED_IP);
    assertLoginSuccessful(sysadmin, subject);

    // now lets check cidr range:
    initHttpReqAndResp();
    String validCIDR = "123.222.123.192/27";
    ipMgr.save(new WhiteListedSysAdminIPAddress(validCIDR));
    req.setRemoteAddr("123.222.123.200"); // valid in range
    assertLoginSuccessful(sysadmin, subject);
    initHttpReqAndResp();
    req.setRemoteAddr("123.222.123.15"); // outside range
    assertLoginFailed(sysadmin, subject);

    sysadmin = logoutAndLoginAsSysAdmin();
    initHttpReqAndResp();
    req.setRemoteAddr("abcde"); // invalid format handled
    assertLoginFailed(sysadmin, subject);

    logoutAndLoginAsSysAdmin();
    initHttpReqAndResp();
    // now let's check login is blocked if is invalid IP
    String INVALID_IP = RandomStringUtils.randomNumeric(5);
    req.setRemoteAddr(INVALID_IP);
    assertLoginFailed(sysadmin, subject);

    // but a non-sysadmin should be able to logon fine
    User user = createAndSaveRandomUser();
    logoutAndLoginAs(user);
    initHttpReqAndResp();
    req.setRemoteAddr(INVALID_IP);
    assertLoginSuccessful(user, subject);

    // now let's set whitelist filter off, unknown IP address should be OK for sysadmin
    initHttpReqAndResp();
    logoutAndLoginAsSysAdmin();
    getBeanOfClass(PropertyHolder.class).setWhiteListedIpsEnabled("false");
    // filter.setWhiteListedIpsEnabled(false);
    req.setRemoteAddr(INVALID_IP);
    assertLoginSuccessful(sysadmin, subject);
  }

  private void assertLoginFailed(User sysadmin, Subject subject) throws Exception {
    assertFalse(ipAuth.isLoginPermitted(req, resp, sysadmin));
    assertTrue(getRedirectUrl().contains(IPWhitelistLoginAuthorizer.REDIRECT_FOR_IP_FAILURE));
    assertFalse(subject.isAuthenticated());
  }

  private void assertLoginSuccessful(User user, Subject subject) throws Exception {
    log.info("auth is {}", ipAuth);
    assertTrue(ipAuth.isLoginPermitted(req, resp, user));
    // redirect url will be the default success login set in shiro, not
    assertErrorRedirectUrlUnset();
    assertTrue(subject.isAuthenticated());
  }

  private String getRedirectUrl() {
    return resp.getHeaderValue("Location").toString();
  }

  private void assertErrorRedirectUrlUnset() {
    assertNull(resp.getHeaderValue("Location"));
  }
}
