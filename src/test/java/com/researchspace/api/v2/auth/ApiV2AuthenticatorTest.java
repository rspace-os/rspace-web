package com.researchspace.api.v2.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.model.User;
import com.researchspace.model.UserAuthenticationMethod;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.OAuthTokenManager;
import com.researchspace.service.UserApiKeyManager;
import com.researchspace.service.UserManager;
import com.researchspace.session.SessionAttributeUtils;
import java.util.Optional;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiV2AuthenticatorTest {

  private final UserApiKeyManager apiKeyManager = mock(UserApiKeyManager.class);
  private final ApiAvailabilityHandler availability = mock(ApiAvailabilityHandler.class);
  private final AnalyticsManager analytics = mock(AnalyticsManager.class);
  private final UserManager userManager = mock(UserManager.class);
  private final Subject subject = mock(Subject.class);
  private final ApiV2Authenticator authenticator =
      new ApiV2Authenticator(
          apiKeyManager, mock(OAuthTokenManager.class), analytics, availability, userManager);

  @BeforeEach
  void setUp() {
    ThreadContext.bind(subject);
  }

  @AfterEach
  void tearDown() {
    ThreadContext.unbindSubject();
  }

  @Test
  void rejectsMissingCredentials() {
    assertThrows(
        ApiV2AuthenticationException.class,
        () -> authenticator.authenticate(new MockHttpServletRequest()));
  }

  @Test
  void optionalAuthenticationReturnsEmptyForAnAnonymousRequest() {
    assertTrue(authenticator.authenticateIfPresent(new MockHttpServletRequest()).isEmpty());
  }

  @Test
  void optionalAuthenticationReusesAnExistingBrowserSession() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    Session session = mock(Session.class);
    User user = mock(User.class);
    when(subject.getPrincipal()).thenReturn("ada");
    when(subject.getSession(false)).thenReturn(session);
    when(session.getAttribute(SessionAttributeUtils.USER)).thenReturn(user);
    when(user.getUsername()).thenReturn("ada");

    assertSame(user, authenticator.authenticateIfPresent(request).orElseThrow());
    assertSame(Boolean.TRUE, request.getAttribute(ApiV2Authenticator.SESSION_REUSED_ATTRIBUTE));
    verify(subject, never()).login(org.mockito.ArgumentMatchers.any(AuthenticationToken.class));
  }

  @Test
  void optionalAuthenticationUsesTheActiveRunAsPrincipalInsteadOfTheCachedAdministrator() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    Session session = mock(Session.class);
    User administrator = mock(User.class);
    User operatedAsUser = mock(User.class);
    when(subject.getPrincipal()).thenReturn("grace");
    when(subject.getSession(false)).thenReturn(session);
    when(session.getAttribute(SessionAttributeUtils.USER)).thenReturn(administrator);
    when(administrator.getUsername()).thenReturn("admin");
    when(userManager.getUserByUsernameNoSession("grace")).thenReturn(operatedAsUser);

    assertSame(operatedAsUser, authenticator.authenticateIfPresent(request).orElseThrow());
    assertSame(Boolean.TRUE, request.getAttribute(ApiV2Authenticator.SESSION_REUSED_ATTRIBUTE));
    verify(session, never()).setAttribute(SessionAttributeUtils.USER, operatedAsUser);
    verify(subject, never()).login(org.mockito.ArgumentMatchers.any(AuthenticationToken.class));
  }

  @Test
  void optionalAuthenticationDoesNotTreatTheAnonymousGuestSessionAsAuthenticated() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    Session session = mock(Session.class);
    User anonymousGuest = mock(User.class);
    when(subject.getPrincipal()).thenReturn("anonymous");
    when(subject.getSession(false)).thenReturn(session);
    when(session.getAttribute(SessionAttributeUtils.USER)).thenReturn(anonymousGuest);
    when(anonymousGuest.getUsername()).thenReturn("anonymous");
    when(anonymousGuest.isAnonymousGuestAccount()).thenReturn(true);

    assertTrue(authenticator.authenticateIfPresent(request).isEmpty());
    assertNull(request.getAttribute(ApiV2Authenticator.SESSION_REUSED_ATTRIBUTE));
  }

  @Test
  void authenticatesApiKeyWithoutUsingV1Authentication() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/users/me");
    request.addHeader("apiKey", "abcde");
    User user = mock(User.class);
    when(user.getUsername()).thenReturn("ada");
    when(apiKeyManager.findUserByKey("abcde")).thenReturn(Optional.of(user));
    when(availability.isApiAvailableForUser(null)).thenReturn(true);
    when(availability.isApiAvailableForUser(user)).thenReturn(true);

    assertSame(user, authenticator.authenticate(request));

    verify(subject).login(org.mockito.ArgumentMatchers.any(AuthenticationToken.class));
    verify(user).setAuthenticatedBy(UserAuthenticationMethod.API_KEY);
    verify(analytics).publicApiUsed(user, request);
  }

  @Test
  void translatesShiroAuthenticationFailureIntoAnApiAuthenticationFailure() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/users/me");
    request.addHeader("apiKey", "abcde");
    User user = mock(User.class);
    when(user.getUsername()).thenReturn("ada");
    when(apiKeyManager.findUserByKey("abcde")).thenReturn(Optional.of(user));
    when(availability.isApiAvailableForUser(null)).thenReturn(true);
    when(availability.isApiAvailableForUser(user)).thenReturn(true);
    doThrow(new UnknownAccountException("no such account"))
        .when(subject)
        .login(org.mockito.ArgumentMatchers.any(AuthenticationToken.class));

    ApiV2AuthenticationException thrown =
        assertThrows(ApiV2AuthenticationException.class, () -> authenticator.authenticate(request));

    assertFalse(thrown.getMessage().contains("no such account"));
  }

  @Test
  void marksTheRequestWhenAnExistingSessionIsReusedSoTheInterceptorLeavesItAlone() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/users/me");
    request.addHeader("apiKey", "abcde");
    User user = mock(User.class);
    when(user.getUsername()).thenReturn("ada");
    when(apiKeyManager.findUserByKey("abcde")).thenReturn(Optional.of(user));
    when(availability.isApiAvailableForUser(null)).thenReturn(true);
    when(availability.isApiAvailableForUser(user)).thenReturn(true);

    authenticator.authenticate(request);

    assertNull(request.getAttribute(ApiV2Authenticator.SESSION_REUSED_ATTRIBUTE));
    verify(subject).login(org.mockito.ArgumentMatchers.any(AuthenticationToken.class));
  }
}
