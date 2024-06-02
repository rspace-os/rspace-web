package com.researchspace.webapp.integrations.figshare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.record.TestFactory;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.UserManager;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.function.Supplier;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.view.RedirectView;

@ExtendWith(MockitoExtension.class)
class FigshareOAuthControllerTest {

  @Mock UserManager userManager;
  @Mock UserConnectionManager userConnectionManager;
  MockHttpServletRequest mockRequest = new MockHttpServletRequest();
  @Mock IPropertyHolder propertyHolder;

  static ResponseEntity<FigshareOAuthController.AccessToken> stubAccessToken() {
    var accessToken = new FigshareOAuthController.AccessToken();
    accessToken.setAccessToken("access-token");
    accessToken.setRefreshToken("refreshtoken");
    accessToken.setExpiresIn(10000L);
    accessToken.setScope("all");
    accessToken.setType("access");
    return ResponseEntity.ok(accessToken);
  }

  static class FigshareOAuthControllerTSS extends FigshareOAuthController {
    @Override
    protected String generateState() {
      return "astate";
    }

    // stubs API call to figshare
    Supplier<ResponseEntity<AccessToken>> tokenExchanger = () -> stubAccessToken();

    @Override
    ResponseEntity<AccessToken> requestAccessToken(
        HttpEntity<Map<String, String>> accessTokenRequestEntity) {
      return tokenExchanger.get();
    }

    // mock various behaviours when verifying 'state' attribute
    Runnable stateVerifier = () -> {};

    @Override
    protected void verifyStateParameter(HttpServletRequest req) {
      stateVerifier.run();
    }

    final long ANY_ID = 22;

    @Override
    long extractIdFromAccount(String accessTokenStr) {
      return ANY_ID;
    }
  }

  @InjectMocks FigshareOAuthControllerTSS figOauthCtrllerTSS;
  private static final String serverUrl = "https://myrspace.com";

  @Test
  void connect() throws UnsupportedEncodingException {
    when(propertyHolder.getServerUrl()).thenReturn(serverUrl);
    RedirectView view = figOauthCtrllerTSS.connect();
    assertTrue(view.getUrl().contains("myrspace.com"));
    assertTrue(view.getUrl().contains("apps%2Ffigshare%2Fredirect_uri"));
  }

  @Test
  void disconnect() {
    figOauthCtrllerTSS.disconnect(() -> "a principal");
    verify(userConnectionManager)
        .deleteByUserAndProvider(
            Mockito.eq(IntegrationsHandler.FIGSHARE_APP_NAME), Mockito.eq("a principal"));
  }

  @Test
  void saveAccessToken() {
    var params = Map.of("code", "my-auth-code", "state", "a-state");
    Mockito.when(userManager.getAuthenticatedUserInSession())
        .thenReturn(TestFactory.createAnyUser("auser"));
    figOauthCtrllerTSS.onAuthorization(params, new ExtendedModelMap(), mockRequest);
    verify(userConnectionManager).save(Mockito.any(UserConnection.class));
  }

  @Test
  void saveAccessTokenNotPerformedIfStateMismatch() {
    var params = Map.of("code", "my-auth-code", "state", "a-state");
    Mockito.when(userManager.getAuthenticatedUserInSession())
        .thenReturn(TestFactory.createAnyUser("auser"));
    figOauthCtrllerTSS.stateVerifier =
        () -> {
          throw new IllegalArgumentException();
        };
    assertThrows(
        IllegalArgumentException.class,
        () -> figOauthCtrllerTSS.onAuthorization(params, new ExtendedModelMap(), mockRequest));
    verify(userConnectionManager, never()).save(Mockito.any(UserConnection.class));
  }

  @Test
  void handleTokenExchangeError() {
    var params = Map.of("code", "my-auth-code", "state", "a-state");
    Mockito.when(userManager.getAuthenticatedUserInSession())
        .thenReturn(TestFactory.createAnyUser("auser"));
    figOauthCtrllerTSS.tokenExchanger =
        () -> {
          throw new HttpClientErrorException(HttpStatus.BAD_REQUEST);
        };
    String viewName =
        figOauthCtrllerTSS.onAuthorization(params, new ExtendedModelMap(), mockRequest);
    assertEquals(viewName, "connect/authorizationError");
    verify(userConnectionManager, never()).save(Mockito.any(UserConnection.class));
  }
}
