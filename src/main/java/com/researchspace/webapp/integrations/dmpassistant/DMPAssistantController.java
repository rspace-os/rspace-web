package com.researchspace.webapp.integrations.dmpassistant;

import static com.researchspace.service.IntegrationsHandler.PROVIDER_USER_ID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dmps.DmpDto;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.service.DMPManager;
import com.researchspace.service.MediaManager;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.integrations.helper.BaseOAuth2Controller;
import com.researchspace.webapp.integrations.helper.ConnectionResultPage;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError.OauthAuthorizationErrorBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;

/**
 * OAuth2 + API proxy controller for DMP Assistant (Portage Network roadmap fork). Modelled on
 * {@code DMPOnlineController}. The OAuth2 authorization-code flow ({@code /connect}, {@code
 * /callback}, {@code /refresh_token}) stores the user's access/refresh token pair in {@code
 * UserConnection}; each proxy endpoint resolves (and if near expiry, refreshes) that token before
 * delegating to the {@link DMPAssistantProvider}.
 */
@Slf4j
@Controller
@RequestMapping("/apps/dmpassistant")
public class DMPAssistantController extends BaseOAuth2Controller {

  static final String APP_NAME = "DMPASSISTANT";
  private static final String CONNECTED_VIEW = "connect/connected";
  private static final String APP_DISPLAY_NAME = "DMP Assistant";
  private static final String CONNECTION_CHANNEL = "rspace.apps.dmpassistant.connection";
  private static final String CONNECTION_TYPE = "DMPASSISTANT_CONNECTED";

  /** Each imported plan costs an upstream fetch plus a media write; cap the batch size. */
  static final int MAX_IMPORT_BATCH_SIZE = 50;

  /** ObjectMapper is thread-safe and expensive to create; share one across imports. */
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired private MediaManager mediaManager;
  @Autowired private DMPManager dmpManager;
  @Autowired private DMPAssistantProvider dmpAssistantProvider;

  @Value("${dmpassistant.base.url}")
  private String baseUrl;

  @Value("${dmpassistant.client.id}")
  private String clientId;

  @Value("${dmpassistant.client.secret}")
  private String clientSecret;

  @Value("${dmpassistant.callback.base.url}")
  private String callbackBaseUrl;

  @Value("${dmpassistant.client.scope}")
  private String scope;

  @Value("${dmpassistant.client.token.expire.threshold:120}")
  private Long timeThreshold;

  private String urlAuthorize;
  private String urlToken;
  private String urlCallback;

  @Setter(value = AccessLevel.PROTECTED) // test purposes
  private RestTemplate restTemplate = new RestTemplate();

  @PostConstruct
  public void init() throws URISyntaxException, MalformedURLException {
    this.urlAuthorize = baseUrl + "/oauth/authorize";
    this.urlToken = baseUrl + "/oauth/token";
    this.urlCallback =
        new URI(getCallbackUrl() + "/apps/dmpassistant/callback").normalize().toString();
    clientId = clientId == null ? "" : StringUtils.strip(clientId);
    clientSecret = clientSecret == null ? "" : StringUtils.strip(clientSecret);
  }

  @PostMapping("/connect")
  public RedirectView connect() {
    String state = generateState();
    String query =
        String.format(
            "?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&state=%s",
            clientId,
            URLEncoder.encode(urlCallback, StandardCharsets.UTF_8),
            URLEncoder.encode(scope, StandardCharsets.UTF_8),
            state);
    return new RedirectView(urlAuthorize + query);
  }

  @GetMapping("/callback")
  public String callback(
      @RequestParam Map<String, String> params,
      Model model,
      Principal principal,
      HttpServletRequest request) {
    OauthAuthorizationErrorBuilder error =
        OauthAuthorizationError.builder().appName("DMP Assistant");
    try {
      verifyStateParameter(request);
      AccessToken accessToken = requestAccessToken(params.get("code"));
      createUserConnection(principal, accessToken);
      log.info("Connected DMP Assistant for user {}", principal.getName());
      ConnectionResultPage.addConnectionAttributes(
          model, APP_DISPLAY_NAME, CONNECTION_CHANNEL, CONNECTION_TYPE);
      return CONNECTED_VIEW;
    } catch (Exception ex) {
      log.error("Couldn't complete the token request on DMP Assistant", ex);
      error.errorMsg("Error during token creation");
      error.errorDetails(ex.getMessage());
      ConnectionResultPage.addError(
          model, APP_DISPLAY_NAME, CONNECTION_CHANNEL, CONNECTION_TYPE, error.build());
      return "connect/connected";
    }
  }

  @DeleteMapping("/connect")
  public void disconnect(Principal principal) {
    int deleted = userConnectionManager.deleteByUserAndProvider(principal.getName(), APP_NAME);
    log.info("Deleted {} DMP Assistant connection(s) for user {}", deleted, principal.getName());
  }

  @PostMapping("/refresh_token")
  public String refreshToken(Model model, Principal principal) {
    OauthAuthorizationErrorBuilder error =
        OauthAuthorizationError.builder().appName("DMP Assistant");
    Optional<UserConnection> optConn = getExistingUserConnection(principal.getName());
    if (optConn.isEmpty()) {
      throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "User connection not found");
    }
    try {
      AccessToken refreshed = refreshAccessToken(optConn.get().getRefreshToken());
      UserConnection conn = optConn.get();
      conn.setAccessToken(refreshed.getAccessToken());
      conn.setRefreshToken(refreshed.getRefreshToken());
      conn.setExpireTime(getExpireTime(refreshed.getExpiresIn()));
      conn.setDisplayName("DMP Assistant refreshed access token");
      userConnectionManager.save(conn);
      log.info("Refreshed DMP Assistant token for user {}", principal.getName());
      ConnectionResultPage.addConnectionAttributes(
          model, APP_DISPLAY_NAME, CONNECTION_CHANNEL, CONNECTION_TYPE);
      return CONNECTED_VIEW;
    } catch (Exception e) {
      log.error("Error while refreshing DMP Assistant token: {}", e.getMessage());
      error.errorMsg("Error during token refresh");
      error.errorDetails(e.getMessage());
      ConnectionResultPage.addError(
          model, APP_DISPLAY_NAME, CONNECTION_CHANNEL, CONNECTION_TYPE, error.build());
      return "connect/connected";
    }
  }

  @GetMapping("/me")
  @ResponseBody
  public AjaxReturnObject<JsonNode> me(Model model, Principal principal) {
    return proxy(model, principal, accessToken -> dmpAssistantProvider.me(accessToken));
  }

  @GetMapping("/plans")
  @ResponseBody
  public AjaxReturnObject<JsonNode> listPlans(
      @RequestParam(name = "page", defaultValue = "1") String page,
      @RequestParam(name = "per_page", defaultValue = "20") String perPage,
      @RequestParam(name = "complete", required = false) Boolean complete,
      Model model,
      Principal principal) {
    return proxy(
        model,
        principal,
        accessToken -> dmpAssistantProvider.listPlans(page, perPage, complete, accessToken));
  }

  @GetMapping("/plans/{id}")
  @ResponseBody
  public AjaxReturnObject<JsonNode> getPlanById(
      @PathVariable("id") String id,
      @RequestParam(name = "complete", required = false) Boolean complete,
      Model model,
      Principal principal) {
    return proxy(
        model,
        principal,
        accessToken -> dmpAssistantProvider.getPlanById(id, complete, accessToken));
  }

  @PostMapping("/plans")
  @ResponseBody
  public AjaxReturnObject<JsonNode> createPlan(
      @RequestBody JsonNode body, Model model, Principal principal) {
    return proxy(
        model, principal, accessToken -> dmpAssistantProvider.createPlan(body, accessToken));
  }

  @PutMapping("/plans/{id}")
  @ResponseBody
  public AjaxReturnObject<JsonNode> editPlanAnswers(
      @PathVariable("id") String id, @RequestBody JsonNode body, Model model, Principal principal) {
    return proxy(
        model,
        principal,
        accessToken -> dmpAssistantProvider.editPlanAnswers(id, body, accessToken));
  }

  @GetMapping("/templates")
  @ResponseBody
  public AjaxReturnObject<JsonNode> listTemplates(Model model, Principal principal) {
    return proxy(model, principal, accessToken -> dmpAssistantProvider.listTemplates(accessToken));
  }

  @GetMapping("/templates/{id}")
  @ResponseBody
  public AjaxReturnObject<JsonNode> getTemplateById(
      @PathVariable("id") String id, Model model, Principal principal) {
    return proxy(
        model, principal, accessToken -> dmpAssistantProvider.getTemplateById(id, accessToken));
  }

  /**
   * Fetches the named plans from DMP Assistant and saves each as a new EcatDocumentFile in the
   * Gallery, then registers a DMPUser row per plan so subsequent exports can attach the DMP. If any
   * single plan fails the whole batch fails — the controller's typed error envelope is returned and
   * no further plans are imported. Plans imported before the failure are kept.
   */
  @PostMapping("/importPlans")
  @ResponseBody
  public AjaxReturnObject<List<JsonNode>> importPlans(
      @RequestBody List<ImportPlanRequest> requests, Model model, Principal principal) {
    if (requests.size() > MAX_IMPORT_BATCH_SIZE) {
      return new AjaxReturnObject<>(
          null,
          getErrorListFromMessageCode(
              "apps.dmpassistant.error.import.batch.too.large", MAX_IMPORT_BATCH_SIZE));
    }
    return proxy(
        model,
        principal,
        accessToken -> {
          User user = userManager.getUserByUsername(principal.getName());
          List<JsonNode> imported = new ArrayList<>(requests.size());
          for (ImportPlanRequest req : requests) {
            imported.add(importSinglePlan(req.getId(), req.getFilename(), accessToken, user));
          }
          return imported;
        });
  }

  private JsonNode importSinglePlan(String id, String filename, String accessToken, User user)
      throws IOException {
    JsonNode plan = dmpAssistantProvider.getPlanById(id, true, accessToken);
    byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(plan);
    String effectiveFilename =
        StringUtils.isBlank(filename)
            ? ("dmp-" + id + ".json")
            : (filename.endsWith(".json") ? filename : filename + ".json");
    EcatDocumentFile file =
        mediaManager.saveNewDMP(effectiveFilename, new ByteArrayInputStream(bytes), user, null);
    if (file == null) {
      // fail the batch visibly rather than registering a DMPUser row with no document
      // and reporting success to the user
      throw new IllegalStateException(
          "saveNewDMP returned null for plan '" + id + "'; gallery document was not created");
    }
    JsonNode dmpNode = plan.has("dmp") ? plan.get("dmp") : plan;
    String title = dmpNode.path("title").asText(filename);
    Optional<DMPUser> existing = dmpManager.findByDmpId(id, user);
    DMPUser dmpUser =
        existing.orElseGet(
            () ->
                new DMPUser(
                    user,
                    new DmpDto(
                        id,
                        title,
                        DMPSource.DMP_ASSISTANT,
                        null,
                        dmpNode.path("dmp_id").path("identifier").asText(null))));
    dmpUser.setDmpDownloadFile(file);
    dmpManager.save(dmpUser);
    return plan;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ImportPlanRequest {
    private String id;
    private String filename;
  }

  private interface ProviderCall<T> {
    T call(String accessToken) throws Exception;
  }

  private <T> AjaxReturnObject<T> proxy(Model model, Principal principal, ProviderCall<T> call) {
    try {
      String accessToken = getExistingAccessToken(model, principal);
      return new AjaxReturnObject<>(call.call(accessToken), null);
    } catch (TokenRefreshFailedException e) {
      log.warn("DMP Assistant token refresh failed; user must reconnect");
      return new AjaxReturnObject<>(
          null, getErrorListFromMessageCode("apps.dmpassistant.error.refresh"));
    } catch (HttpStatusCodeException e) {
      // Log the full upstream message (which may include the response body, e.g. a
      // Cloudflare HTML challenge page on 403) but never surface that to the user —
      // build a clean, status-coded message from the bundle for the error envelope.
      log.warn("DMP Assistant request failed: {}", e.getMessage());
      String statusLabel = e.getStatusCode().value() + " " + e.getStatusCode().getReasonPhrase();
      return new AjaxReturnObject<>(
          null, getErrorListFromMessageCode("apps.dmpassistant.error.upstream", statusLabel));
    } catch (Exception e) {
      log.warn("Error connecting to DMP Assistant", e);
      return new AjaxReturnObject<>(
          null, getErrorListFromMessageCode("apps.dmpassistant.error.connect"));
    }
  }

  private String getExistingAccessToken(Model model, Principal principal) {
    Optional<UserConnection> optConn = getExistingUserConnection(principal.getName());
    if (optConn.isEmpty()) {
      throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "User connection not found");
    }
    if (StringUtils.isBlank(optConn.get().getAccessToken())) {
      throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "Access token not found");
    }
    Long expireTime = optConn.get().getExpireTime();
    if (expireTime != null && expireTime - Instant.now().toEpochMilli() < timeThreshold * 1000L) {
      if (!CONNECTED_VIEW.equals(refreshToken(model, principal))) {
        // proceeding with the stale token would only yield an opaque upstream 401; tell
        // the user to reconnect instead
        throw new TokenRefreshFailedException();
      }
      optConn = getExistingUserConnection(principal.getName());
    }
    return optConn.get().getAccessToken();
  }

  /** Thrown when a near-expiry token refresh fails; the user needs to reconnect. */
  private static class TokenRefreshFailedException extends RuntimeException {}

  protected Optional<UserConnection> getExistingUserConnection(String username) {
    Optional<UserConnection> conn =
        userConnectionManager.findByUserNameProviderName(username, APP_NAME);
    if (conn.isEmpty()) {
      log.debug("No DMP Assistant connection found for user {}", username);
    }
    return conn;
  }

  private void createUserConnection(Principal principal, AccessToken accessToken) {
    UserConnection conn = new UserConnection();
    conn.setAccessToken(accessToken.getAccessToken());
    conn.setRefreshToken(accessToken.getRefreshToken());
    conn.setExpireTime(getExpireTime(accessToken.getExpiresIn()));
    conn.setDisplayName("DMP Assistant access token");
    conn.setId(new UserConnectionId(principal.getName(), APP_NAME, PROVIDER_USER_ID));
    userConnectionManager.save(conn);
  }

  private AccessToken requestAccessToken(String code) throws HttpStatusCodeException {
    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "authorization_code");
    formData.add("code", code);
    return callAuthTokenEndPoint(formData);
  }

  private AccessToken refreshAccessToken(String refreshToken) throws HttpStatusCodeException {
    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "refresh_token");
    formData.add("refresh_token", refreshToken);
    return callAuthTokenEndPoint(formData);
  }

  private AccessToken callAuthTokenEndPoint(MultiValueMap<String, String> formData)
      throws HttpStatusCodeException {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/x-www-form-urlencoded");
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    formData.add("client_id", clientId);
    formData.add("client_secret", clientSecret);
    formData.add("redirect_uri", urlCallback);
    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
    AccessToken body =
        restTemplate
            .exchange(URI.create(urlToken), HttpMethod.POST, request, AccessToken.class)
            .getBody();
    if (body == null) {
      throw new IllegalStateException("DMP Assistant token endpoint returned an empty body");
    }
    return body;
  }

  private long getExpireTime(Long expiresIn) {
    return Instant.now().toEpochMilli() + (expiresIn == null ? 0 : expiresIn * 1000L);
  }

  private URL getCallbackUrl() throws MalformedURLException {
    if (StringUtils.isEmpty(callbackBaseUrl)) {
      return new URL(properties.getServerUrl());
    }
    return new URL(callbackBaseUrl);
  }
}
