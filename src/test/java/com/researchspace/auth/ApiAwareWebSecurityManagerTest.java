package com.researchspace.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.web.subject.support.WebDelegatingSubject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ApiAwareWebSecurityManagerTest {

  private ApiAwareWebSecurityManager securityManager;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private Session session;

  @BeforeEach
  public void setUp() {
    securityManager = new ApiAwareWebSecurityManager();
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    session = mock(Session.class);
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
    ApiAwareWebSecurityManager.doStatelessLogin(
        () -> securityManager.beforeSuccessfulLogin(subjectWithSession()));

    verify(request, never()).changeSessionId();
  }

  @Test
  public void ordinaryLoginStillRotatesSessionId() {
    securityManager.beforeSuccessfulLogin(subjectWithSession());

    verify(request).changeSessionId();
  }

  @Test
  public void statelessMarkIsClearedAfterLogin() {
    ApiAwareWebSecurityManager.doStatelessLogin(() -> {});

    securityManager.beforeSuccessfulLogin(subjectWithSession());

    verify(request).changeSessionId();
  }

  @Test
  public void nestedStatelessLoginKeepsOuterMarkUntilOuterCallReturns() {
    ApiAwareWebSecurityManager.doStatelessLogin(
        () -> {
          ApiAwareWebSecurityManager.doStatelessLogin(() -> {});
          securityManager.beforeSuccessfulLogin(subjectWithSession());
        });

    verify(request, never()).changeSessionId();

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
