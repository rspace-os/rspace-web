package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.auth.ApiAuthenticationException;
import com.researchspace.api.v1.model.NewOAuthTokenResponse;
import com.researchspace.auth.PasswordGrantGuessLimiter;
import com.researchspace.auth.password.LoginVerificationBusyException;
import com.researchspace.model.User;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.IReauthenticator;
import com.researchspace.service.OAuthAppManager;
import com.researchspace.service.OAuthTokenManager;
import com.researchspace.service.ReauthenticationResult;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.UserManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectRetrievalFailureException;

@ExtendWith(MockitoExtension.class)
public class OAuthClientControllerTest {

  @Mock private UserManager userManager;
  @Mock private OAuthTokenManager tokenManager;
  @Mock private OAuthAppManager appManager;
  @Mock private IReauthenticator reauthenticator;
  @Mock private PasswordGrantGuessLimiter guessLimiter;
  @Mock private ApiAvailabilityHandler apiHandler;
  @Mock private SystemPropertyPermissionManager systemPropertyMgr;
  @InjectMocks private OAuthClientController controller;

  private final User user = new User("bob");
  private final MockHttpServletRequest request = new MockHttpServletRequest();

  @BeforeEach
  public void enableApiAndOAuth() {
    user.setEnabled(true);
    when(apiHandler.isApiAvailableForUser(isNull())).thenReturn(true);
    when(systemPropertyMgr.isPropertyAllowed(
            isNull(User.class), eq(SystemPropertyName.API_OAUTH_AUTHENTICATION)))
        .thenReturn(true);
  }

  /** The client passed the early check, then its app was deleted before the token was created. */
  private void clientValidThenGoneAtTokenCreation() {
    when(appManager.isClientSecretCorrect("client", "secret")).thenReturn(true);
    when(userManager.getUserByUsernameOrAlias("bob")).thenReturn(user);
    when(apiHandler.isApiAvailableForUser(user)).thenReturn(true);
    when(guessLimiter.tryAcquire("bob")).thenReturn(true);
    when(reauthenticator.reauthenticate(user, "pw")).thenReturn(ReauthenticationResult.OK);
  }

  @Test
  public void failedTokenCreationIsAnErrorNotAnEmptySuccess() {
    clientValidThenGoneAtTokenCreation();
    when(tokenManager.createNewToken(eq("client"), eq("secret"), eq(user), any()))
        .thenReturn(
            new ServiceOperationResult<>(null, false, "ClientId or ClientSecret incorrect"));

    ApiAuthenticationException e =
        assertThrows(ApiAuthenticationException.class, () -> getToken(false));
    assertEquals("oauth.errors.tokenCreationFailed", e.getMessageKey());
  }

  @Test
  public void failedJwtTokenCreationIsAnErrorNotAnEmptySuccess() {
    clientValidThenGoneAtTokenCreation();
    when(tokenManager.createNewJwtToken(eq("client"), eq("secret"), eq(user), any()))
        .thenReturn(
            new ServiceOperationResult<>(null, false, "ClientId or ClientSecret incorrect"));

    ApiAuthenticationException e =
        assertThrows(ApiAuthenticationException.class, () -> getToken(true));
    assertEquals("oauth.errors.tokenCreationFailed", e.getMessageKey());
  }

  @Test
  public void invalidClientIsRefusedBeforeAnyPasswordCheck() {
    when(appManager.isClientSecretCorrect("client", "secret")).thenReturn(false);

    ApiAuthenticationException e =
        assertThrows(ApiAuthenticationException.class, () -> getToken(false));
    assertEquals("oauth.errors.tokenCreationFailed", e.getMessageKey());
    verifyNoInteractions(userManager, reauthenticator, guessLimiter);
  }

  @Test
  public void usernameOverTheGuessLimitCostsOneSentinelCheckInsteadOfAPasswordCheck() {
    when(appManager.isClientSecretCorrect("client", "secret")).thenReturn(true);
    when(userManager.getUserByUsernameOrAlias("bob")).thenReturn(user);
    when(apiHandler.isApiAvailableForUser(user)).thenReturn(true);
    when(guessLimiter.tryAcquire("bob")).thenReturn(false);

    ApiAuthenticationException e =
        assertThrows(ApiAuthenticationException.class, () -> getToken(false));
    assertEquals("oauth.errors.invalidCredentials", e.getMessageKey());
    verify(guessLimiter, times(1)).padWithSentinelCheck("bob", "pw");
    verifyNoInteractions(reauthenticator);
  }

  @Test
  public void wrongPasswordIsCheckedOnceAndNotPadded() {
    when(appManager.isClientSecretCorrect("client", "secret")).thenReturn(true);
    when(userManager.getUserByUsernameOrAlias("bob")).thenReturn(user);
    when(apiHandler.isApiAvailableForUser(user)).thenReturn(true);
    when(guessLimiter.tryAcquire("bob")).thenReturn(true);
    when(reauthenticator.reauthenticate(user, "pw")).thenReturn(ReauthenticationResult.WRONG);

    ApiAuthenticationException e =
        assertThrows(ApiAuthenticationException.class, () -> getToken(false));
    assertEquals("oauth.errors.invalidCredentials", e.getMessageKey());
    verify(reauthenticator, times(1)).reauthenticate(user, "pw");
    verify(guessLimiter, never()).padWithSentinelCheck(any(), any());
    verify(guessLimiter, never()).recordSuccess(any());
  }

  @Test
  public void unknownUsernameCostsOneSentinelCheckInsteadOfAPasswordCheck() {
    when(appManager.isClientSecretCorrect("client", "secret")).thenReturn(true);
    when(userManager.getUserByUsernameOrAlias("bob"))
        .thenThrow(new ObjectRetrievalFailureException(User.class, "bob"));

    ApiAuthenticationException e =
        assertThrows(ApiAuthenticationException.class, () -> getToken(false));
    assertEquals("oauth.errors.invalidCredentials", e.getMessageKey());
    verify(guessLimiter, times(1)).padWithSentinelCheck("bob", "pw");
    verifyNoMoreInteractions(guessLimiter);
    verifyNoInteractions(reauthenticator);
  }

  @Test
  public void busyPasswordCheckIsRefusedAsBusyAndNotCounted() {
    clientValidThenGoneAtTokenCreation();
    when(reauthenticator.reauthenticate(user, "pw")).thenReturn(ReauthenticationResult.BUSY);

    assertThrows(LoginVerificationBusyException.class, () -> getToken(false));
    verify(guessLimiter).uncount("bob");
    verify(guessLimiter, never()).recordSuccess(any());
    verifyNoInteractions(tokenManager);
  }

  private NewOAuthTokenResponse getToken(boolean isJwt) {
    return controller.getToken("password", "bob", "pw", null, "secret", "client", isJwt, request);
  }
}
