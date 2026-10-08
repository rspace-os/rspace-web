package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.auth.ApiAuthenticationException;
import com.researchspace.api.v1.model.NewOAuthTokenResponse;
import com.researchspace.auth.PasswordGrantGuessLimiter;
import com.researchspace.model.User;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.IReauthenticator;
import com.researchspace.service.OAuthAppManager;
import com.researchspace.service.OAuthTokenManager;
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

  /** The client passed the early check, then its app was deleted before the token was created. */
  @BeforeEach
  public void clientValidThenGoneAtTokenCreation() {
    user.setEnabled(true);
    when(apiHandler.isApiAvailableForUser(isNull())).thenReturn(true);
    when(systemPropertyMgr.isPropertyAllowed(
            isNull(User.class), eq(SystemPropertyName.API_OAUTH_AUTHENTICATION)))
        .thenReturn(true);
    when(appManager.isClientSecretCorrect("client", "secret")).thenReturn(true);
    when(userManager.getUserByUsernameOrAlias("bob")).thenReturn(user);
    when(apiHandler.isApiAvailableForUser(user)).thenReturn(true);
    when(guessLimiter.tryAcquire("bob")).thenReturn(true);
    when(reauthenticator.reauthenticate(user, "pw")).thenReturn(true);
  }

  @Test
  public void failedTokenCreationIsAnErrorNotAnEmptySuccess() {
    when(tokenManager.createNewToken(eq("client"), eq("secret"), eq(user), any()))
        .thenReturn(
            new ServiceOperationResult<>(null, false, "ClientId or ClientSecret incorrect"));

    ApiAuthenticationException e =
        assertThrows(ApiAuthenticationException.class, () -> getToken(false));
    assertEquals("oauth.errors.tokenCreationFailed", e.getMessageKey());
  }

  @Test
  public void failedJwtTokenCreationIsAnErrorNotAnEmptySuccess() {
    when(tokenManager.createNewJwtToken(eq("client"), eq("secret"), eq(user), any()))
        .thenReturn(
            new ServiceOperationResult<>(null, false, "ClientId or ClientSecret incorrect"));

    ApiAuthenticationException e =
        assertThrows(ApiAuthenticationException.class, () -> getToken(true));
    assertEquals("oauth.errors.tokenCreationFailed", e.getMessageKey());
  }

  private NewOAuthTokenResponse getToken(boolean isJwt) {
    return controller.getToken("password", "bob", "pw", null, "secret", "client", isJwt, request);
  }
}
