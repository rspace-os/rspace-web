package com.researchspace.webapp.integrations.dmpassistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.webapp.controller.AjaxReturnObject;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.support.BindingAwareModelMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Pure unit tests for {@link DMPAssistantController} modelled on {@code DryadOAuthControllerTest}.
 * Each OAuth-token endpoint is exercised against a {@link MockRestServiceServer} bound to the
 * controller's {@link RestTemplate}. Proxy endpoints delegate to a mocked {@link
 * DMPAssistantProvider}.
 */
class DMPAssistantControllerTest {

  private static final String BASE_URL = "https://dmp-pgd.ca";
  private static final String CLIENT_ID = "rspace-client";
  private static final String CLIENT_SECRET = "shhh";
  private static final String SERVER_URL = "http://localhost:8080";
  private static final String SCOPE = "read write";
  private static final String USERNAME = "auser";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @InjectMocks private DMPAssistantController controller;
  @Mock private UserConnectionManager userConnectionManager;
  @Mock private UserConnection userConnection;
  @Mock private IPropertyHolder properties;
  @Mock private DMPAssistantProvider dmpAssistantProvider;

  private RestTemplate restTemplate;
  private MockRestServiceServer mockServer;
  private final Principal principal = () -> USERNAME;

  @BeforeEach
  void setUp() throws Exception {
    openMocks(this);
    restTemplate = new RestTemplate();
    ReflectionTestUtils.setField(controller, "restTemplate", restTemplate);
    ReflectionTestUtils.setField(controller, "baseUrl", BASE_URL);
    ReflectionTestUtils.setField(controller, "clientId", CLIENT_ID);
    ReflectionTestUtils.setField(controller, "clientSecret", CLIENT_SECRET);
    ReflectionTestUtils.setField(controller, "callbackBaseUrl", "");
    ReflectionTestUtils.setField(controller, "scope", SCOPE);
    ReflectionTestUtils.setField(controller, "timeThreshold", 120L);
    when(properties.getServerUrl()).thenReturn(SERVER_URL);
    controller.init();
    mockServer = MockRestServiceServer.createServer(restTemplate);
  }

  @Test
  void connectRedirectsToAuthorizeEndpoint() throws Exception {
    RedirectView view = controller.connect();

    assertEquals(
        BASE_URL
            + "/oauth/authorize?client_id="
            + CLIENT_ID
            + "&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapps%2Fdmpassistant%2Fcallback"
            + "&response_type=code&scope=read+write",
        view.getUrl());
  }

  @Test
  void callbackExchangesCodeAndPersistsUserConnection() throws Exception {
    String tokenResponse =
        "{\"access_token\":\"NEW_ACCESS_TOKEN\","
            + "\"refresh_token\":\"NEW_REFRESH_TOKEN\","
            + "\"expires_in\":7200}";
    mockServer
        .expect(requestTo(BASE_URL + "/oauth/token"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(tokenResponse, MediaType.APPLICATION_JSON));
    when(userConnectionManager.save(any(UserConnection.class))).thenReturn(userConnection);

    String result =
        controller.callback(Map.of("code", "AUTH_CODE"), new ExtendedModelMap(), principal);

    mockServer.verify();
    assertEquals("connect/dmpassistant/connected", result);
    verify(userConnectionManager).save(any(UserConnection.class));
  }

  @Test
  void callbackReturnsErrorPageOnTokenFailure() throws Exception {
    mockServer
        .expect(requestTo(BASE_URL + "/oauth/token"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    String result = controller.callback(Map.of("code", "BAD"), new ExtendedModelMap(), principal);

    assertEquals("connect/authorizationError", result);
  }

  @Test
  void refreshTokenUsesRefreshGrantAndUpdatesConnection() throws Exception {
    when(userConnection.getRefreshToken()).thenReturn("OLD_REFRESH");
    when(userConnection.getAccessToken()).thenReturn("OLD_ACCESS");
    when(userConnectionManager.findByUserNameProviderName(USERNAME, "DMPASSISTANT"))
        .thenReturn(Optional.of(userConnection));
    when(userConnectionManager.save(any(UserConnection.class))).thenReturn(userConnection);

    mockServer
        .expect(requestTo(BASE_URL + "/oauth/token"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                "{\"access_token\":\"REFRESHED\",\"refresh_token\":\"NEW_REFRESH\",\"expires_in\":7200}",
                MediaType.APPLICATION_JSON));

    String result = controller.refreshToken(new ExtendedModelMap(), principal);

    mockServer.verify();
    assertEquals("connect/dmpassistant/connected", result);
    verify(userConnection).setAccessToken("REFRESHED");
    verify(userConnection).setRefreshToken("NEW_REFRESH");
    verify(userConnection).setDisplayName("DMP Assistant refreshed access token");
  }

  @Test
  void disconnectDeletesUserConnection() {
    when(userConnectionManager.deleteByUserAndProvider(anyString(), anyString())).thenReturn(1);
    controller.disconnect(principal);
    verify(userConnectionManager).deleteByUserAndProvider(USERNAME, "DMPASSISTANT");
  }

  @Test
  void isConnectionAliveTrueWhenMeEndpointReturns200() {
    when(userConnectionManager.findByUserNameProviderName(USERNAME, "DMPASSISTANT"))
        .thenReturn(Optional.of(userConnection));
    when(userConnection.getAccessToken()).thenReturn("ACCESS");
    mockServer
        .expect(requestTo(BASE_URL + "/api/v2/me"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ACCESS"))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertTrue(controller.isConnectionAlive(principal));
    mockServer.verify();
  }

  @Test
  void isConnectionAliveFalseWhenNoUserConnection() {
    when(userConnectionManager.findByUserNameProviderName(USERNAME, "DMPASSISTANT"))
        .thenReturn(Optional.empty());

    assertFalse(controller.isConnectionAlive(principal));
  }

  @Test
  void isConnectionAliveFalseWhenMeEndpointReturns401() {
    when(userConnectionManager.findByUserNameProviderName(USERNAME, "DMPASSISTANT"))
        .thenReturn(Optional.of(userConnection));
    when(userConnection.getAccessToken()).thenReturn("STALE");
    mockServer
        .expect(requestTo(BASE_URL + "/api/v2/me"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertFalse(controller.isConnectionAlive(principal));
  }

  @Test
  void heartbeatProxyDelegatesToProviderUnauthenticated() throws Exception {
    JsonNode payload = MAPPER.readTree("{\"application\":\"DMP Assistant\"}");
    when(dmpAssistantProvider.heartbeat()).thenReturn(payload);

    AjaxReturnObject<JsonNode> result = controller.heartbeat();

    assertNotNull(result.getData());
    assertEquals("DMP Assistant", result.getData().get("application").asText());
  }

  @Test
  void mePrxyDelegatesToProviderWithUsersToken() throws Exception {
    stubAccessToken("USER_TOKEN");
    JsonNode payload = MAPPER.readTree("{\"email\":\"u@example.ca\"}");
    when(dmpAssistantProvider.me(eq("USER_TOKEN"))).thenReturn(payload);

    AjaxReturnObject<JsonNode> result = controller.me(new BindingAwareModelMap(), principal);

    assertEquals("u@example.ca", result.getData().get("email").asText());
  }

  @Test
  void listPlansProxyForwardsPaginationAndComplete() throws Exception {
    stubAccessToken("USER_TOKEN");
    JsonNode payload = MAPPER.readTree("{\"items\":[]}");
    when(dmpAssistantProvider.listPlans(eq("2"), eq("25"), eq(true), eq("USER_TOKEN")))
        .thenReturn(payload);

    AjaxReturnObject<JsonNode> result =
        controller.listPlans("2", "25", true, new BindingAwareModelMap(), principal);

    assertEquals(0, result.getData().get("items").size());
  }

  @Test
  void getPlanByIdProxyForwardsCompleteFlag() throws Exception {
    stubAccessToken("USER_TOKEN");
    JsonNode payload = MAPPER.readTree("{\"dmp\":{\"id\":3}}");
    when(dmpAssistantProvider.getPlanById(eq("3"), eq(false), eq("USER_TOKEN")))
        .thenReturn(payload);

    AjaxReturnObject<JsonNode> result =
        controller.getPlanById("3", false, new BindingAwareModelMap(), principal);

    assertEquals(3, result.getData().get("dmp").get("id").asInt());
  }

  @Test
  void createPlanProxyPostsBodyToProvider() throws Exception {
    stubAccessToken("USER_TOKEN");
    JsonNode body = MAPPER.readTree("{\"dmp\":{\"title\":\"X\"}}");
    JsonNode response = MAPPER.readTree("{\"dmp\":{\"id\":1,\"title\":\"X\"}}");
    when(dmpAssistantProvider.createPlan(eq(body), eq("USER_TOKEN"))).thenReturn(response);

    AjaxReturnObject<JsonNode> result =
        controller.createPlan(body, new BindingAwareModelMap(), principal);

    assertEquals(1, result.getData().get("dmp").get("id").asInt());
  }

  @Test
  void editPlanAnswersProxyPutsBodyToProvider() throws Exception {
    stubAccessToken("USER_TOKEN");
    JsonNode body = MAPPER.readTree("{\"answers\":[]}");
    JsonNode response = MAPPER.readTree("{\"ok\":true}");
    when(dmpAssistantProvider.editPlanAnswers(eq("9"), eq(body), eq("USER_TOKEN")))
        .thenReturn(response);

    AjaxReturnObject<JsonNode> result =
        controller.editPlanAnswers("9", body, new BindingAwareModelMap(), principal);

    assertEquals(true, result.getData().get("ok").asBoolean());
  }

  @Test
  void listTemplatesProxyDelegates() throws Exception {
    stubAccessToken("USER_TOKEN");
    JsonNode payload = MAPPER.readTree("{\"items\":[{\"id\":1}]}");
    when(dmpAssistantProvider.listTemplates(eq("USER_TOKEN"))).thenReturn(payload);

    AjaxReturnObject<JsonNode> result =
        controller.listTemplates(new BindingAwareModelMap(), principal);

    assertEquals(1, result.getData().get("items").get(0).get("id").asInt());
  }

  @Test
  void getTemplateByIdProxyDelegates() throws Exception {
    stubAccessToken("USER_TOKEN");
    JsonNode payload = MAPPER.readTree("{\"id\":42}");
    when(dmpAssistantProvider.getTemplateById(eq("42"), eq("USER_TOKEN"))).thenReturn(payload);

    AjaxReturnObject<JsonNode> result =
        controller.getTemplateById("42", new BindingAwareModelMap(), principal);

    assertEquals(42, result.getData().get("id").asInt());
  }

  private void stubAccessToken(String token) {
    when(userConnectionManager.findByUserNameProviderName(USERNAME, "DMPASSISTANT"))
        .thenReturn(Optional.of(userConnection));
    when(userConnection.getAccessToken()).thenReturn(token);
    when(userConnection.getRefreshToken()).thenReturn("REFRESH");
    when(userConnection.getExpireTime()).thenReturn(System.currentTimeMillis() + 60L * 60 * 1000);
  }
}
