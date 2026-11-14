package com.researchspace.webapp.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.auth.SidVerificationException;
import com.researchspace.auth.password.LoginVerificationBusyException;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserSignupException;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.List;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Filter bookkeeping needs committed accounts because policy attempts use fresh transactions. */
class StandaloneLoginBookkeepingIT extends RealTransactionSpringTestBase {
  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  void signupSidAndBusyRefusalsDoNotCountButPasswordFailuresPersist() throws Exception {
    User user = createInitAndLoginAnyUser();
    StandaloneShiroFormAuthFilterExt filter = new StandaloneShiroFormAuthFilterExt();
    filter.setUserMgr(userMgr);
    filter.setMessages(messages);
    filter.setProperties(applicationContext.getBean(IPropertyHolder.class));
    var token = new UsernamePasswordToken(user.getUsername(), "wrong password");
    List<AuthenticationException> excluded =
        List.of(
            new AuthenticationException(null, new UserSignupException("signup exception")),
            new AuthenticationException(null, new SidVerificationException("sid exception")),
            new LoginVerificationBusyException("busy"));
    for (AuthenticationException failure : excluded) {
      MockHttpServletRequest request = request(user);
      filter.onLoginFailure(token, failure, request, new MockHttpServletResponse());
      assertEquals(
          failure.getCause() == null ? null : failure.getCause().getMessage(),
          request.getAttribute("checkedExceptionMessage"));
      assertEquals(0, userMgr.get(user.getId()).getNumConsecutiveLoginFailures());
    }
    MockHttpServletRequest wrongPasswordRequest = request(user);
    filter.onLoginFailure(
        token,
        new AuthenticationException("wrong password"),
        wrongPasswordRequest,
        new MockHttpServletResponse());
    assertNull(wrongPasswordRequest.getAttribute("checkedExceptionMessage"));
    assertEquals(1, userMgr.get(user.getId()).getNumConsecutiveLoginFailures());
  }

  private MockHttpServletRequest request(User user) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter("username", user.getUsername());
    return request;
  }
}
