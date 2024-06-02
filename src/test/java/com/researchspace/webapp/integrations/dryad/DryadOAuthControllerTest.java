package com.researchspace.webapp.integrations.dryad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.record.TestFactory;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.UserManager;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;

class DryadOAuthControllerTest {

  @InjectMocks private DryadOAuthController dryadOAuthController;
  @Mock private UserConnectionManager userConnectionManager;
  @Mock private UserConnection userConnection;
  @Mock RestTemplate restTemplateMock;
  @Mock private UserManager userManager;
  @Mock private IPropertyHolder properties;

  MockRestServiceServer mockServer;
  MockHttpServletRequest mockRequest = new MockHttpServletRequest();

  @BeforeEach
  void setUp() {
    openMocks(this);
    ReflectionTestUtils.setField(dryadOAuthController, "restTemplate", restTemplateMock);
    ReflectionTestUtils.setField(dryadOAuthController, "baseUrl", "https://sandbox.datadryad.org");
    ReflectionTestUtils.setField(dryadOAuthController, "clientId", "client-id");
    ReflectionTestUtils.setField(dryadOAuthController, "clientSecret", "client-secret");
    mockServer = MockRestServiceServer.createServer(restTemplateMock);
    when(userConnectionManager.save(any(UserConnection.class))).thenReturn(userConnection);
    when(userConnectionManager.findByUserNameProviderName(anyString(), anyString()))
        .thenReturn(Optional.of(userConnection));
    when(userManager.getAuthenticatedUserInSession()).thenReturn(TestFactory.createAnyUser("test"));
    when(properties.getServerUrl()).thenReturn("http://localhost:8080");
  }

  @Test
  void testDryadConnect() {
    RedirectView response = dryadOAuthController.connect();
    assertEquals(
        "https://sandbox.datadryad.org/oauth/authorize?client_id=client-id&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapps%2Fdryad%2Fcallback&response_type=code&scope=all",
        response.getUrl());
  }

  @Test
  void testDryadCallback() {
    when(restTemplateMock.postForEntity(
            anyString(), any(HttpEntity.class), eq(DryadOAuthController.DryadAccessToken.class)))
        .thenReturn(new ResponseEntity<>(getTestDryadAccessToken(), HttpStatus.OK));
    when(restTemplateMock.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(DryadOAuthController.DryadOAuthTest.class)))
        .thenReturn(new ResponseEntity<>(getTestDryadUser(), HttpStatus.OK));
    Map<String, String> params = Map.of("code", "my-auth-code", "state", "a-state");
    String result = dryadOAuthController.callback(params, new ExtendedModelMap(), () -> "auser");
    verify(userConnectionManager).save(any(UserConnection.class));
    assertEquals("connect/dryad/connected", result);
  }

  @Test
  void testDryadDisconnect() {
    when(userConnectionManager.deleteByUserAndProvider(anyString(), anyString())).thenReturn(1);
    dryadOAuthController.disconnect(() -> "anyUser");
    verify(userConnectionManager).deleteByUserAndProvider(anyString(), anyString());
  }

  private DryadOAuthController.DryadOAuthTest getTestDryadUser() {
    DryadOAuthController.DryadOAuthTest dryadOAuthTest = new DryadOAuthController.DryadOAuthTest();
    dryadOAuthTest.setUserId(100L);
    dryadOAuthTest.setMessage("Welcome application owner RSOPS Researchspace");
    return dryadOAuthTest;
  }

  private DryadOAuthController.DryadAccessToken getTestDryadAccessToken() {
    DryadOAuthController.DryadAccessToken accessToken = new DryadOAuthController.DryadAccessToken();
    accessToken.setAccessToken("access-token");
    accessToken.setCreatedAt(Instant.now().toEpochMilli());
    accessToken.setExpiresIn(10000L);
    accessToken.setScope("all");
    accessToken.setType("access");
    return accessToken;
  }

  private DryadOAuthController.DryadAccessToken getTestDryadAccessTokenError() {
    DryadOAuthController.DryadAccessToken accessToken = new DryadOAuthController.DryadAccessToken();
    accessToken.setError("invalid_client");
    accessToken.setErrorDescription(
        "Client authentication failed due to unknown client, no client authentication included, or"
            + " unsupported authentication method.");
    return accessToken;
  }
}
