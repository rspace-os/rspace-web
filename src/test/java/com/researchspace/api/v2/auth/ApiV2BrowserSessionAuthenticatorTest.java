package com.researchspace.api.v2.auth;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import com.researchspace.session.SessionAttributeUtils;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiV2BrowserSessionAuthenticatorTest {

  private final UserManager userManager = mock(UserManager.class);
  private final ApiV2BrowserSessionAuthenticator authenticator =
      new ApiV2BrowserSessionAuthenticator(userManager);

  @AfterEach
  void clearSubject() {
    ThreadContext.remove();
  }

  @Test
  void doesNotCreateAMissingSession() {
    MockHttpServletRequest request = new MockHttpServletRequest();

    assertTrue(authenticator.authenticateIfPresent(request).isEmpty());
    assertTrue(request.getSession(false) == null);
    verifyNoInteractions(userManager);
  }

  @Test
  void reloadsTheCurrentPrincipalInsteadOfUsingStaleSessionAuthorizationFacts() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.getSession(true);
    Subject subject = mock(Subject.class);
    Session session = mock(Session.class);
    User staleSessionUser = mock(User.class);
    User currentUser = mock(User.class);
    when(subject.isAuthenticated()).thenReturn(true);
    when(subject.getSession(false)).thenReturn(session);
    when(subject.getPrincipal()).thenReturn("user1");
    when(session.getAttribute(SessionAttributeUtils.USER)).thenReturn(staleSessionUser);
    when(userManager.getUserByUsername("user1", true)).thenReturn(currentUser);
    ThreadContext.bind(subject);

    ApiV2Caller caller = authenticator.authenticateIfPresent(request).orElseThrow();
    assertSame(currentUser, caller.subject());
    assertSame(currentUser, caller.actor());
    verify(userManager).getUserByUsername("user1", true);
  }

  @Test
  void usesCurrentPrincipalRatherThanStaleSessionUserDuringRunAs() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.getSession(true);
    Subject subject = mock(Subject.class);
    Session session = mock(Session.class);
    PrincipalCollection previousPrincipals = mock(PrincipalCollection.class);
    User target = mock(User.class);
    User actor = mock(User.class);
    User staleSessionUser = mock(User.class);
    when(subject.isAuthenticated()).thenReturn(true);
    when(subject.isRunAs()).thenReturn(true);
    when(subject.getSession(false)).thenReturn(session);
    when(subject.getPrincipal()).thenReturn("target1");
    when(subject.getPreviousPrincipals()).thenReturn(previousPrincipals);
    when(previousPrincipals.getPrimaryPrincipal()).thenReturn("sysadmin1");
    when(session.getAttribute(SessionAttributeUtils.USER)).thenReturn(staleSessionUser);
    when(session.getAttribute(SessionAttributeUtils.IS_RUN_AS)).thenReturn(Boolean.TRUE);
    when(userManager.getUserByUsername("target1", true)).thenReturn(target);
    when(userManager.getUserByUsername("sysadmin1", true)).thenReturn(actor);
    ThreadContext.bind(subject);

    ApiV2Caller caller = authenticator.authenticateIfPresent(request).orElseThrow();

    assertSame(target, caller.subject());
    assertSame(actor, caller.actor());
  }
}
