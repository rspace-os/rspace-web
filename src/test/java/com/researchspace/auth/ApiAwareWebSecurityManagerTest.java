package com.researchspace.auth;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.subject.support.WebDelegatingSubject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ApiAwareWebSecurityManagerTest {

  private ApiAwareWebSecurityManager securityManager;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private Session session;
  private Subject loginSubject;
  private AuthenticationToken token;

  @BeforeEach
  public void setUp() {
    securityManager = new ApiAwareWebSecurityManager();
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    session = mock(Session.class);
    loginSubject = mock(Subject.class);
    token = mock(AuthenticationToken.class);
  }

  private WebDelegatingSubject subjectWithSession() {
    return new WebDelegatingSubject(
        new SimplePrincipalCollection("user1a", "realm"),
        true,
        null,
        session,
        request,
        response,
        securityManager);
  }

  @Test
  public void statelessApiLoginKeepsArrivingSessionId() {
    doAnswer(
            invocation -> {
              securityManager.beforeSuccessfulLogin(subjectWithSession());
              return null;
            })
        .when(loginSubject)
        .login(token);

    ApiAwareWebSecurityManager.doStatelessLogin(loginSubject, token);

    verify(loginSubject).login(token);
    verify(request, never()).changeSessionId();
  }

  @Test
  public void ordinaryLoginStillRotatesSessionId() {
    securityManager.beforeSuccessfulLogin(subjectWithSession());

    verify(request).changeSessionId();
  }

  @Test
  public void statelessMarkIsClearedAfterLogin() {
    ApiAwareWebSecurityManager.doStatelessLogin(loginSubject, token);

    securityManager.beforeSuccessfulLogin(subjectWithSession());

    verify(request).changeSessionId();
  }

  @Test
  public void nestedStatelessLoginKeepsOuterMarkUntilOuterCallReturns() {
    Subject innerSubject = mock(Subject.class);
    doAnswer(
            invocation -> {
              ApiAwareWebSecurityManager.doStatelessLogin(innerSubject, token);
              securityManager.beforeSuccessfulLogin(subjectWithSession());
              return null;
            })
        .when(loginSubject)
        .login(token);

    ApiAwareWebSecurityManager.doStatelessLogin(loginSubject, token);

    verify(request, never()).changeSessionId();

    securityManager.beforeSuccessfulLogin(subjectWithSession());

    verify(request).changeSessionId();
  }

  @Test
  public void statelessMarkIsClearedWhenTheLoginThrows() {
    doThrow(new AuthenticationException("bad api key")).when(loginSubject).login(token);

    assertThrows(
        AuthenticationException.class,
        () -> ApiAwareWebSecurityManager.doStatelessLogin(loginSubject, token));

    securityManager.beforeSuccessfulLogin(subjectWithSession());

    verify(request).changeSessionId();
  }

  @Test
  public void loginWithoutSessionRotatesNothing() {
    WebDelegatingSubject noSession =
        new WebDelegatingSubject(
            new SimplePrincipalCollection("user1a", "realm"),
            true,
            null,
            null,
            request,
            response,
            securityManager);

    securityManager.beforeSuccessfulLogin(noSession);

    verify(request, never()).changeSessionId();
  }
}
