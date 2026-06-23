package com.researchspace.webapp.integrations.dmpassistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.DMPManager;
import com.researchspace.service.MediaManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.controller.AjaxReturnObject;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.support.BindingAwareModelMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Pure unit tests for {@link DMPAssistantController} modelled on {@code DryadOAuthControllerTest}.
 * Each OAuth-token endpoint is exercised against a {@link MockRestServiceServer} bound to the
 * controller's {@link RestTemplate}. Proxy endpoints delegate to a mocked {@link
 * DMPAssistantProvider} with the user's resolved access token, and translate {@link
 * org.springframework.web.client.HttpStatusCodeException} responses into the controller's {@link
 * AjaxReturnObject} error contract.
 */
class DMPAssistantControllerTest {

  private static final String BASE_URL = "https://dmp-pgd.ca";
  private static final String CLIENT_ID = "rspace-client";
  private static final String CLIENT_SECRET = "shhh";
  private static final String SERVER_URL = "http://localhost:8080";
  private static final String SCOPE = "read write";
  private static final String USERNAME = "auser";
  private static final String STATE = "test-state";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Test-specific subclass: the real state helpers in BaseOAuth2Controller store the nonce in the
   * Shiro session, which is unavailable in a plain unit test. Modelled on
   * FigshareOAuthControllerTest.
   */
  static class DMPAssistantControllerTSS extends DMPAssistantController {
    Runnable stateVerifier = () -> {};

    @Override
    protected String generateState() {
      return STATE;
    }

    @Override
    protected void verifyStateParameter(javax.servlet.http.HttpServletRequest request) {
      stateVerifier.run();
    }
  }

  @InjectMocks private DMPAssistantControllerTSS controller;
  @Mock private UserConnectionManager userConnectionManager;
  @Mock private UserConnection userConnection;
  @Mock private IPropertyHolder properties;
  @Mock private DMPAssistantProvider dmpAssistantProvider;
  @Mock private MessageSourceUtils messages;
  @Mock private UserManager userManager;
  @Mock private MediaManager mediaManager;
  @Mock private DMPManager dmpManager;

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
    // BaseController.messages is @Autowired in production; inject it explicitly here so the
    // bundle-resolving error branches in proxy() can run. The mock echoes the key + args so
    // any HTML or other content passed by the controller would surface in the resulting
    // message — tests asserting "no HTML in the user-facing string" remain meaningful.
    controller.setMessageSource(messages);
    when(messages.getMessage(anyString(), any()))
        .thenAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              Object[] args = invocation.getArgument(1);
              StringBuilder sb = new StringBuilder(key);
              if (args != null) {
                for (Object arg : args) {
                  sb.append(":").append(arg);
                }
              }
              return sb.toString();
            });
    when(messages.getMessage(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void connectRedirectsToAuthorizeEndpointWithCsrfState() throws Exception {
    RedirectView view = controller.connect();

    assertEquals(
        BASE_URL
            + "/oauth/authorize?client_id="
            + CLIENT_ID
            + "&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapps%2Fdmpassistant%2Fcallback"
            + "&response_type=code&scope=read+write&state="
            + STATE,
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
        controller.callback(
            Map.of("code", "AUTH_CODE", "state", STATE),
            new ExtendedModelMap(),
            principal,
            new MockHttpServletRequest());

    mockServer.verify();
    assertEquals("connect/connected", result);
    verify(userConnectionManager).save(any(UserConnection.class));
  }

  @Test
  void callbackReturnsErrorPageOnTokenFailure() throws Exception {
    mockServer
        .expect(requestTo(BASE_URL + "/oauth/token"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    String result =
        controller.callback(
            Map.of("code", "BAD", "state", STATE),
            new ExtendedModelMap(),
            principal,
            new MockHttpServletRequest());

    mockServer.verify();
    assertEquals("connect/connected", result);
  }

  @Test
  void callbackReportsDescriptiveErrorWhenTokenResponseBodyIsEmpty() throws Exception {
    mockServer
        .expect(requestTo(BASE_URL + "/oauth/token"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess()); // HTTP 200 with an empty body

    ExtendedModelMap model = new ExtendedModelMap();
    String result =
        controller.callback(
            Map.of("code", "AUTH_CODE", "state", STATE),
            model,
            principal,
            new MockHttpServletRequest());

    mockServer.verify();
    assertEquals("connect/connected", result);
    String connectionError = (String) model.getAttribute("connectionError");
    assertTrue(
        connectionError.contains("empty body"),
        "Expected a descriptive empty-body message but was: " + connectionError);
    verify(userConnectionManager, never()).save(any(UserConnection.class));
  }

  @Test
  void callbackRejectsMismatchedStateWithoutExchangingCode() throws Exception {
    controller.stateVerifier =
        () -> {
          throw new IllegalStateException(
              "The OAuth2 'state' parameter is missing or doesn't match.");
        };

    String result =
        controller.callback(
            Map.of("code", "AUTH_CODE", "state", "attacker-state"),
            new ExtendedModelMap(),
            principal,
            new MockHttpServletRequest());

    assertEquals("connect/connected", result);
    verify(userConnectionManager, never()).save(any(UserConnection.class));
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
    assertEquals("connect/connected", result);
    verify(userConnection).setAccessToken("REFRESHED");
    verify(userConnection).setRefreshToken("NEW_REFRESH");
    verify(userConnection).setDisplayName("DMP Assistant refreshed access token");
  }

  @Test
  void refreshTokenReturnsErrorViewWhenTokenEndpointFails() {
    when(userConnection.getRefreshToken()).thenReturn("OLD_REFRESH");
    when(userConnectionManager.findByUserNameProviderName(USERNAME, "DMPASSISTANT"))
        .thenReturn(Optional.of(userConnection));
    mockServer
        .expect(requestTo(BASE_URL + "/oauth/token"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST));
    ExtendedModelMap model = new ExtendedModelMap();

    String result = controller.refreshToken(model, principal);

    mockServer.verify();
    assertEquals("connect/connected", result);
    assertNotNull(model.getAttribute("connectionError"));
  }

  @Test
  void refreshTokenThrowsNotFoundWhenNoConnection() {
    when(userConnectionManager.findByUserNameProviderName(USERNAME, "DMPASSISTANT"))
        .thenReturn(Optional.empty());

    HttpClientErrorException ex =
        assertThrows(
            HttpClientErrorException.class,
            () -> controller.refreshToken(new ExtendedModelMap(), principal));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  void disconnectDeletesUserConnection() {
    when(userConnectionManager.deleteByUserAndProvider(anyString(), anyString())).thenReturn(1);
    controller.disconnect(principal);
    verify(userConnectionManager).deleteByUserAndProvider(USERNAME, "DMPASSISTANT");
  }

  @Test
  void proxyRefreshesTokenWhenNearExpiryAndForwardsRefreshedToken() throws Exception {
    when(userConnectionManager.findByUserNameProviderName(USERNAME, "DMPASSISTANT"))
        .thenReturn(Optional.of(userConnection));
    // first read passes the blank-token check with the stale token; once the refresh has
    // saved the connection, the re-read returns the refreshed token
    when(userConnection.getAccessToken()).thenReturn("STALE", "REFRESHED");
    when(userConnection.getRefreshToken()).thenReturn("OLD_REFRESH");
    when(userConnection.getExpireTime())
        .thenReturn(System.currentTimeMillis() + 60_000L); // within the 120s threshold
    when(userConnectionManager.save(any(UserConnection.class))).thenReturn(userConnection);
    mockServer
        .expect(requestTo(BASE_URL + "/oauth/token"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                "{\"access_token\":\"REFRESHED\",\"refresh_token\":\"NEW_REFRESH\",\"expires_in\":7200}",
                MediaType.APPLICATION_JSON));
    JsonNode payload = MAPPER.readTree("{\"email\":\"u@example.ca\"}");
    when(dmpAssistantProvider.me(eq("REFRESHED"))).thenReturn(payload);

    AjaxReturnObject<JsonNode> result = controller.me(new BindingAwareModelMap(), principal);

    mockServer.verify(); // the refresh grant was actually exchanged
    verify(userConnection).setAccessToken("REFRESHED");
    assertNotNull(result.getData());
    assertEquals("u@example.ca", result.getData().get("email").asText());
  }

  @Test
  void proxyReportsRefreshFailureWhenNearExpiryRefreshFails() throws Exception {
    when(userConnectionManager.findByUserNameProviderName(USERNAME, "DMPASSISTANT"))
        .thenReturn(Optional.of(userConnection));
    when(userConnection.getAccessToken()).thenReturn("STALE");
    when(userConnection.getRefreshToken()).thenReturn("OLD_REFRESH");
    when(userConnection.getExpireTime())
        .thenReturn(System.currentTimeMillis() + 60_000L); // within the 120s threshold
    mockServer
        .expect(requestTo(BASE_URL + "/oauth/token"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST));

    AjaxReturnObject<JsonNode> result = controller.me(new BindingAwareModelMap(), principal);

    mockServer.verify(); // the refresh exchange was actually attempted
    assertNull(result.getData());
    assertNotNull(result.getError());
    String surfaced = result.getError().getAllErrorMessagesAsStringsSeparatedBy(" ");
    assertEquals("apps.dmpassistant.error.refresh", surfaced);
  }

  @Test
  void meProxyDelegatesToProviderWithUsersToken() throws Exception {
    stubAccessToken("USER_TOKEN");
    JsonNode payload = MAPPER.readTree("{\"email\":\"u@example.ca\"}");
    when(dmpAssistantProvider.me(eq("USER_TOKEN"))).thenReturn(payload);

    AjaxReturnObject<JsonNode> result = controller.me(new BindingAwareModelMap(), principal);

    assertNotNull(result.getData());
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

  @Test
  void proxyReturnsUpstreamErrorEnvelopeWhenNoUserConnection() throws Exception {
    when(userConnectionManager.findByUserNameProviderName(USERNAME, "DMPASSISTANT"))
        .thenReturn(Optional.empty());

    AjaxReturnObject<JsonNode> result = controller.me(new BindingAwareModelMap(), principal);

    // getExistingAccessToken throws HttpClientErrorException(NOT_FOUND), which the proxy's
    // HttpStatusCodeException branch translates into the status-coded upstream envelope
    assertNull(result.getData());
    assertNotNull(result.getError());
    String surfaced = result.getError().getAllErrorMessagesAsStringsSeparatedBy(" ");
    assertEquals("apps.dmpassistant.error.upstream:404 Not Found", surfaced);
  }

  @Test
  void proxyReturnsUpstreamErrorEnvelopeWhenStoredAccessTokenIsBlank() throws Exception {
    when(userConnectionManager.findByUserNameProviderName(USERNAME, "DMPASSISTANT"))
        .thenReturn(Optional.of(userConnection));
    when(userConnection.getAccessToken()).thenReturn("");

    AjaxReturnObject<JsonNode> result = controller.me(new BindingAwareModelMap(), principal);

    assertNull(result.getData());
    assertNotNull(result.getError());
    String surfaced = result.getError().getAllErrorMessagesAsStringsSeparatedBy(" ");
    assertEquals("apps.dmpassistant.error.upstream:404 Not Found", surfaced);
  }

  @Test
  void importPlansFailsWhenGalleryDocumentIsNotCreated() throws Exception {
    stubAccessToken("USER_TOKEN");
    User user = new User(USERNAME);
    when(userManager.getUserByUsername(USERNAME)).thenReturn(user);
    JsonNode plan =
        MAPPER.readTree(
            "{\"dmp\":{\"title\":\"Plan One\",\"dmp_id\":"
                + "{\"identifier\":\"https://dmp-pgd.ca/api/v2/plans/101\"}}}");
    when(dmpAssistantProvider.getPlanById(eq("101"), eq(true), eq("USER_TOKEN"))).thenReturn(plan);
    when(mediaManager.saveNewDMP(anyString(), any(), eq(user), any())).thenReturn(null);

    AjaxReturnObject<List<JsonNode>> result =
        controller.importPlans(
            List.of(new DMPAssistantController.ImportPlanRequest("101", "plan-one.json")),
            new BindingAwareModelMap(),
            principal);

    // a missing gallery document must fail the batch, not report success
    assertNull(result.getData());
    assertNotNull(result.getError());
    verify(dmpManager, never()).save(any(DMPUser.class));
  }

  @Test
  void importPlansRejectsBatchesOverTheCap() throws Exception {
    List<DMPAssistantController.ImportPlanRequest> requests = new ArrayList<>();
    for (int i = 0; i < 51; i++) {
      requests.add(
          new DMPAssistantController.ImportPlanRequest(String.valueOf(i), "plan-" + i + ".json"));
    }

    AjaxReturnObject<List<JsonNode>> result =
        controller.importPlans(requests, new BindingAwareModelMap(), principal);

    assertNull(result.getData());
    assertNotNull(result.getError());
    String surfaced = result.getError().getAllErrorMessagesAsStringsSeparatedBy(" ");
    assertEquals("apps.dmpassistant.error.import.batch.too.large:50", surfaced);
    verifyNoInteractions(dmpAssistantProvider);
  }

  @Test
  void proxyTranslatesHttpStatusCodeExceptionIntoErrorList() throws Exception {
    stubAccessToken("USER_TOKEN");
    when(dmpAssistantProvider.me(eq("USER_TOKEN")))
        .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "no token"));

    AjaxReturnObject<JsonNode> result = controller.me(new BindingAwareModelMap(), principal);

    assertNull(result.getData());
    assertNotNull(result.getError());
  }

  /**
   * Cloudflare-protected DMP Assistant endpoints can return a 403 with an HTML challenge page in
   * the response body. The proxy must never bubble that HTML out via the AjaxReturnObject — it has
   * to substitute a clean, status-coded message resolved through the i18n bundle.
   */
  @Test
  void proxyStripsResponseBodyFromHttpStatusCodeExceptionMessage() throws Exception {
    stubAccessToken("USER_TOKEN");
    String htmlBody =
        "<!DOCTYPE html><html><head><title>Just a moment...</title></head><body>"
            + "<script>cloudflare challenge</script></body></html>";
    HttpClientErrorException ex =
        HttpClientErrorException.create(
            HttpStatus.FORBIDDEN, "Forbidden", null, htmlBody.getBytes(), null);
    when(dmpAssistantProvider.me(eq("USER_TOKEN"))).thenThrow(ex);

    AjaxReturnObject<JsonNode> result = controller.me(new BindingAwareModelMap(), principal);

    assertNull(result.getData());
    assertNotNull(result.getError());
    String surfaced = result.getError().getAllErrorMessagesAsStringsSeparatedBy(" ");
    assertFalse(
        surfaced.contains("<!DOCTYPE")
            || surfaced.contains("<html")
            || surfaced.contains("<script"),
        "HTML body must not leak into the user-facing error message but was: " + surfaced);
    assertFalse(
        surfaced.contains("cloudflare"),
        "Upstream challenge content must not leak into the user-facing error message");
  }

  private void stubAccessToken(String token) {
    when(userConnectionManager.findByUserNameProviderName(USERNAME, "DMPASSISTANT"))
        .thenReturn(Optional.of(userConnection));
    when(userConnection.getAccessToken()).thenReturn(token);
    when(userConnection.getRefreshToken()).thenReturn("REFRESH");
    when(userConnection.getExpireTime()).thenReturn(System.currentTimeMillis() + 60L * 60 * 1000);
  }
}
