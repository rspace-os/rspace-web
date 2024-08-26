package com.researchspace.webapp.integrations.digitalcommonsdata;

import static com.researchspace.dcd.utils.DigitalCommonsDataUtils.getApiBaseUrl;
import static com.researchspace.dcd.utils.DigitalCommonsDataUtils.getAuthBaseUrl;
import static com.researchspace.service.IntegrationsHandler.DIGITAL_COMMONS_DATA_APP_NAME;

import com.researchspace.dcd.model.DcdAccessToken;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.webapp.integrations.helper.BaseOAuth2Controller;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping("/apps/digitalcommonsdata")
public class DigitalCommonsDataController extends BaseOAuth2Controller {

  @Value("${dcd.client.id}")
  private String clientId;

  @Value("${dcd.client.secret}")
  private String clientSecret;

  @Value("${dcd.client.scope}")
  private String clientScope;

  @Value("${dcd.base.url}")
  private String webBaseUrl;

  @Value("${dcd.callback.base.url}")
  private String callbackBaseUrl;

  private @Autowired UserConnectionManager userConnectionManager;

  private RestTemplate restTemplate;

  private static String URL_AUTH_END_POINT;
  private static String URL_TOKEN_END_POINT;
  private static String URL_DATASET_DELETE_END_POINT;
  private static String URL_CALLBACK;
  private static final String TEMP_TOKEN = "TEMP";
  private static final String FAKE_DATASET_ID = "FAKE_ID";

  public DigitalCommonsDataController() {
    this.restTemplate = new RestTemplate();
  }

  @PostConstruct
  public void init() throws URISyntaxException {
    URL_AUTH_END_POINT = getAuthBaseUrl(this.webBaseUrl) + "/oauth2/authorize";
    URL_TOKEN_END_POINT = getAuthBaseUrl(this.webBaseUrl) + "/oauth2/token";
    URL_DATASET_DELETE_END_POINT =
        getApiBaseUrl(this.webBaseUrl) + "/active-data-entities/datasets/drafts/" + FAKE_DATASET_ID;
    URL_CALLBACK = getCallbackUrl() + "/apps/digitalcommonsdata/callback";
    URL_CALLBACK = new URI(URL_CALLBACK).normalize().toString();
    clientId = clientId == null ? "" : StringUtils.strip(clientId);
    clientSecret = clientSecret == null ? "" : StringUtils.strip(clientSecret);
  }

  @NotNull
  private String getCallbackUrl() {
    if (StringUtils.isBlank(this.callbackBaseUrl)) {
      return this.properties.getServerUrl();
    }
    return this.callbackBaseUrl;
  }

  @PostMapping("/connect")
  public RedirectView connect(Model model, Principal principal) throws MalformedURLException {
    String redirectUrl = "";
    try {
      redirectUrl = triggerLoginClient(principal);
    } catch (HttpStatusCodeException e) {
      OauthAuthorizationError error =
          OauthAuthorizationError.builder()
              .appName("DigitalCommonsData")
              .errorMsg("Exception during token exchange")
              .errorDetails(e.getResponseBodyAsString())
              .build();
      model.addAttribute("error", error);

      redirectUrl = "connect/authorizationError";
    }
    return new RedirectView(redirectUrl);
  }

  private String triggerLoginClient(Principal principal) throws HttpStatusCodeException {
    String randomWord = UUID.randomUUID().toString();
    UserConnection conn = createUserConnection(principal);
    conn.setSecret(randomWord);
    conn.setDisplayName("DigitalCommonsData Client Access Secret");
    userConnectionManager.save(conn);
    log.info("Connected DigitalCommonsData for user {}", principal.getName());

    return URL_AUTH_END_POINT
        + "?response_type=code"
        + "&client_id="
        + clientId
        + "&redirect_uri="
        + URLEncoder.encode(URL_CALLBACK, StandardCharsets.UTF_8)
        + "&scope="
        + URLEncoder.encode(clientScope, StandardCharsets.UTF_8)
        + "&state="
        + randomWord;
  }

  @DeleteMapping("/connect")
  public void disconnect(Principal principal) {
    int deletedConnCount =
        userConnectionManager.deleteByUserAndProvider(
            DIGITAL_COMMONS_DATA_APP_NAME, principal.getName());
    log.info(
        "Deleted {} Digital Commons Data connection(s) for user {}",
        deletedConnCount,
        principal.getName());
  }

  @GetMapping("/callback")
  public String callback(@RequestParam Map<String, String> params, Model model, Principal principal)
      throws IOException, URISyntaxException, HttpClientErrorException {
    DcdAccessToken accessToken;
    Optional<UserConnection> optUserConnection = getUserConnection(principal.getName());
    if (optUserConnection.isEmpty()) {
      throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "User connection not found");
    }
    UserConnection userConnection = optUserConnection.get();
    String clientCodeReturned = params.get("code");
    // now (having the client secret code) get the access TOKEN
    if (userConnection.getSecret() != null
        && !userConnection.getSecret().equals(params.get("state"))) {
      throw new HttpClientErrorException(
          HttpStatus.UNAUTHORIZED, "User Connection's state is not recognized");
    }

    try {
      accessToken = getAccessToken(clientCodeReturned);
    } catch (HttpStatusCodeException e) {
      OauthAuthorizationError error =
          OauthAuthorizationError.builder()
              .appName("DigitalCommonsData")
              .errorMsg("Exception during token exchange")
              .errorDetails(e.getResponseBodyAsString())
              .build();
      model.addAttribute("error", error);

      return "connect/authorizationError";
    }

    userConnection.setAccessToken(accessToken.getAccessToken());
    userConnection.setRefreshToken(accessToken.getRefreshToken());
    userConnection.setExpireTime(getExpireTime(accessToken.getExpiresIn()));
    userConnection.setDisplayName("DigitalCommonsData access token");
    userConnectionManager.save(userConnection);
    log.info("Connected DigitalCommonsData for user {}", principal.getName());

    return "connect/dcd/connected";
  }

  private DcdAccessToken getAccessToken(String clientCode) throws HttpStatusCodeException {
    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "authorization_code");
    formData.add("code", clientCode);
    return callAuthTokenEndPoint(formData);
  }

  private DcdAccessToken refreshAccessToken(String refreshToken) throws HttpStatusCodeException {
    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "refresh_token");
    formData.add("refresh_token", refreshToken);
    return callAuthTokenEndPoint(formData);
  }

  private DcdAccessToken callAuthTokenEndPoint(MultiValueMap<String, String> formData)
      throws HttpStatusCodeException {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    headers.setBasicAuth(clientId, clientSecret);
    formData.add("redirect_uri", URL_CALLBACK);
    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

    return restTemplate
        .exchange(URL_TOKEN_END_POINT, HttpMethod.POST, request, DcdAccessToken.class)
        .getBody();
  }

  @GetMapping("/refresh_token")
  public String refreshToken(Model model, Principal principal) {
    DcdAccessToken accessToken;
    Optional<UserConnection> optUserConnection = getUserConnection(principal.getName());
    if (optUserConnection.isEmpty()) {
      throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "User connection not found");
    }
    if (StringUtils.isBlank(optUserConnection.get().getRefreshToken())) {
      throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "Refresh token not found");
    }
    UserConnection userConnection = optUserConnection.get();

    try {
      accessToken = refreshAccessToken(userConnection.getRefreshToken());
    } catch (HttpStatusCodeException e) {
      OauthAuthorizationError error =
          OauthAuthorizationError.builder()
              .appName("DigitalCommonsData")
              .errorMsg("Exception during token refresh")
              .errorDetails(e.getResponseBodyAsString())
              .build();
      model.addAttribute("error", error);

      return "connect/authorizationError";
    }

    userConnection.setAccessToken(accessToken.getAccessToken());
    userConnection.setRefreshToken(accessToken.getRefreshToken());
    userConnection.setExpireTime(getExpireTime(accessToken.getExpiresIn()));
    userConnection.setDisplayName("DigitalCommonsData refreshed access token");
    userConnectionManager.save(userConnection);
    log.info("Connected DigitalCommonsData for user {}", principal.getName());

    return "connected";
  }

  @GetMapping("/test_connection")
  public Boolean isConnectionAlive(Principal principal) {
    Optional<UserConnection> optUserConnection = getUserConnection(principal.getName());

    if (optUserConnection.isEmpty()) {
      return Boolean.FALSE;
    }
    String accessToken = optUserConnection.get().getAccessToken();

    if (StringUtils.isNotBlank(accessToken)) {
      String expectedMsg =
          "404 Not Found: \"{\"message\":\"Draft dataset '" + FAKE_DATASET_ID + "' not found\"}\"";
      try {

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        restTemplate.exchange(
            URL_DATASET_DELETE_END_POINT,
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            Object.class);

      } catch (HttpClientErrorException clientEx) {
        if (expectedMsg.equals(clientEx.getMessage())) {
          return Boolean.TRUE;
        }
      } catch (Exception e) {
        log.error("Couldn't perform test action {}", e.getMessage());
        return Boolean.FALSE;
      }
    }
    return Boolean.TRUE;
  }

  private UserConnection createUserConnection(Principal principal) {
    UserConnection conn = new UserConnection();
    conn.setAccessToken(TEMP_TOKEN);
    conn.setExpireTime(getExpireTime(300L));
    conn.setDisplayName("DigitalCommonsData TEMP Token");
    conn.setId(
        new UserConnectionId(
            principal.getName(), DIGITAL_COMMONS_DATA_APP_NAME, "ProviderUserIdNotNeeded"));
    return conn;
  }

  public Optional<UserConnection> getUserConnection(String username) {
    Optional<UserConnection> optConn =
        userConnectionManager.findByUserNameProviderName(username, DIGITAL_COMMONS_DATA_APP_NAME);
    if (!optConn.isPresent()) {
      log.error("No Digital Commons Data connection found for user {}", username);
    }
    return optConn;
  }

  private long getExpireTime(Long expiresInSeconds) {
    return Instant.now().toEpochMilli() + (expiresInSeconds * 1000);
  }

  /* For testing purposes */
  protected void setRestTemplate(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }
}
