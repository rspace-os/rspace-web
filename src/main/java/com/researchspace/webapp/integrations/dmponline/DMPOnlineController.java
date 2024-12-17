package com.researchspace.webapp.integrations.dmponline;

import static com.researchspace.service.IntegrationsHandler.DMPONLINE_APP_NAME;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMP;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.service.DMPManager;
import com.researchspace.service.MediaManager;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.integrations.helper.BaseOAuth2Controller;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError.OauthAuthorizationErrorBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Controller
@RequestMapping("/apps/dmponline")
public class DMPOnlineController extends BaseOAuth2Controller {

  @Autowired private MediaManager mediaManager;
  @Autowired private UserManager userManager;
  @Autowired private DMPManager dmpManager;

  @Value("${dmponline.base.url}")
  private String baseUrl;

  @Value("${dmponline.client.id}")
  private String clientId;

  @Value("${dmponline.client.secret}")
  private String clientSecret;

  @Value("${dmponline.callback.base.url}")
  private String callbackBaseUrl;

  @Value("${dmponline.client.scope}")
  private String scope;

  @Value("${dmponline.client.token.expire.threshold:120}")
  private Long timeThreshold; // default 2 minutes

  private static String URL_AUTH_END_POINT;
  private static String URL_AUTH_TOKEN_INFO;
  private static String URL_TOKEN_END_POINT;
  private static String URL_DMP_PLANS;
  private static String URL_CALLBACK;
  private static final String TEMP_TOKEN = "TEMP";

  @Setter(value = AccessLevel.PROTECTED) // test purposes
  private RestTemplate restTemplate;

  public DMPOnlineController() {
    this.restTemplate = new RestTemplate();
  }

  @PostConstruct
  public void init() throws URISyntaxException, MalformedURLException {
    URL_AUTH_END_POINT = this.baseUrl + "/oauth/authorize";
    URL_TOKEN_END_POINT = this.baseUrl + "/oauth/token";
    URL_AUTH_TOKEN_INFO = this.baseUrl + "/oauth/token/info";
    URL_DMP_PLANS = this.baseUrl + "/api/v2/plans";
    URL_CALLBACK = getCallbackUrl() + "/apps/dmponline/callback";
    URL_CALLBACK = new URI(URL_CALLBACK).normalize().toString();
    clientId = clientId == null ? "" : StringUtils.strip(clientId);
    clientSecret = clientSecret == null ? "" : StringUtils.strip(clientSecret);
  }

  @PostMapping("/connect")
  public RedirectView connect() throws MalformedURLException {
    String pathAndQuery =
        String.format(
            "?client_id=%s&redirect_uri=%s&response_type=code&scope=%s",
            clientId, URLEncoder.encode(URL_CALLBACK, StandardCharsets.UTF_8), scope);
    return new RedirectView(URL_AUTH_END_POINT + pathAndQuery);
  }

  @GetMapping("/callback")
  public String callback(@RequestParam Map<String, String> params, Model model, Principal principal)
      throws IOException, URISyntaxException, HttpClientErrorException {
    String redirectResult;
    OauthAuthorizationErrorBuilder error = OauthAuthorizationError.builder().appName("DMPonline");
    try {
      AccessToken accessToken = requestAccessToken(params.get("code"));

      createUserConnection(principal, accessToken);
      log.info("Connected DMPonline for user {}", principal.getName());
      redirectResult = "connect/dmponline/connected";
    } catch (Exception ex) {
      error.errorMsg("Error during token creation");
      error.errorMsg(ex.getMessage());
      model.addAttribute("error", error.build());
      redirectResult = "connect/authorizationError";
    }
    return redirectResult;
  }

  @DeleteMapping("/connect")
  public void disconnect(Principal principal) {
    int deletedConnCount =
        userConnectionManager.deleteByUserAndProvider(DMPONLINE_APP_NAME, principal.getName());
    log.info(
        "Deleted {} DMPonline connection(s) for user {}", deletedConnCount, principal.getName());
  }

  @GetMapping("/test_connection")
  public Boolean isConnectionAlive(Principal principal) {
    Optional<UserConnection> optUserConnection = getExistingUserConnection(principal.getName());
    if (optUserConnection.isEmpty()) {
      return Boolean.FALSE;
    }

    Boolean isConnectionAlive;
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(optUserConnection.get().getAccessToken());
      Long expireIn =
          restTemplate
              .exchange(
                  URL_AUTH_TOKEN_INFO, HttpMethod.GET, new HttpEntity<>(headers), AccessToken.class)
              .getBody()
              .getExpiresIn();

      if (expireIn < timeThreshold) { // if less than X minutes
        isConnectionAlive = Boolean.FALSE;
      } else {
        isConnectionAlive = Boolean.TRUE;
      }

    } catch (Exception e) {
      log.error("Couldn't perform test action {}", e.getMessage());
      isConnectionAlive = Boolean.FALSE;
    }

    return isConnectionAlive;
  }

  @PostMapping("/refresh_token")
  public String refreshToken(Model model, Principal principal) {
    AccessToken accessToken;
    OauthAuthorizationErrorBuilder error = OauthAuthorizationError.builder().appName("DMPonline");
    String redirectResult = null;
    Optional<UserConnection> optUserConnection = getExistingUserConnection(principal.getName());
    if (optUserConnection.isEmpty()) {
      throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "User connection not found");
    }
    try {
      accessToken = refreshAccessToken(getExistingRefreshToken(principal));

      UserConnection userConnection = optUserConnection.get();
      userConnection.setAccessToken(accessToken.getAccessToken());
      userConnection.setRefreshToken(accessToken.getRefreshToken());
      userConnection.setExpireTime(getExpireTime(accessToken.getExpiresIn()));
      userConnection.setDisplayName("DMPonline refreshed access token");
      userConnectionManager.save(userConnection);
      log.info("Connected DMPonline for user {}", principal.getName());
      redirectResult = "connect/dmponline/connected";

    } catch (Exception e) {
      error.errorMsg("Error during token refresh");
      error.errorDetails(e.getMessage());
      model.addAttribute("error", error.build());
      redirectResult = "connect/authorizationError";
    }

    return redirectResult;
  }

  @GetMapping("/plans")
  @ResponseBody
  public AjaxReturnObject<JsonNode> listDMPs(
      @RequestParam(name = "page") String page,
      @RequestParam(name = "per_page") String per_page,
      Model model,
      Principal principal) {
    try {
      String accessToken = getExistingAccessToken(model, principal);
      return new AjaxReturnObject(
          restTemplate
              .exchange(
                  UriComponentsBuilder.fromUriString(URL_DMP_PLANS)
                      .queryParam("page", page)
                      .queryParam("per_page", per_page)
                      .build()
                      .toUri(),
                  HttpMethod.GET,
                  new HttpEntity<>(getHttpHeadersWithToken(accessToken)),
                  JsonNode.class)
              .getBody(),
          null);
    } catch (HttpClientErrorException | URISyntaxException | MalformedURLException e) {
      log.warn("error connecting to DMPonline", e);
      return new AjaxReturnObject<>(null, ErrorList.of("Error connecting to DMPonline."));
    }
  }

  @PostMapping("/importPlan")
  @ResponseBody
  public AjaxReturnObject<JsonNode> importDmp(
      @RequestParam(name = "id") String id, // url to dmp
      @RequestParam(name = "filename") String filename,
      Model model,
      Principal principal)
      throws URISyntaxException, IOException {

    User user = userManager.getAuthenticatedUserInSession();
    String accessToken = getExistingAccessToken(model, principal);

    var dmps =
        restTemplate
            .exchange(
                new URL(id).toURI(),
                HttpMethod.GET,
                new HttpEntity<>(getHttpHeadersWithToken(accessToken)),
                JsonNode.class)
            .getBody()
            .get("items")
            .elements();
    var dmpWrappedObject = dmps.next();
    var dmpObject = dmpWrappedObject.get("dmp");

    ObjectMapper objectMapper = new ObjectMapper();
    String json = objectMapper.writeValueAsString(dmpObject);
    InputStream is = new ByteArrayInputStream(json.getBytes());
    var file = mediaManager.saveNewDMP(filename, is, user, null);

    DMP dmp = new DMP(id, filename);
    var dmpUser = dmpManager.findByDmpId(dmp.getDmpId(), user).orElse(new DMPUser(user, dmp));
    if (file != null) {
      dmpUser.setDmpDownloadPdf(file);
    } else {
      log.warn("Unexpected null DMP PDF - did download work?");
    }
    dmpManager.save(dmpUser);

    return new AjaxReturnObject(dmpObject, null);
  }

  private String getExistingAccessToken(Model model, Principal principal)
      throws URISyntaxException, MalformedURLException {
    Optional<UserConnection> optUserConnection = getExistingUserConnection(principal.getName());
    if (optUserConnection.isEmpty()) {
      throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "User connection not found");
    }
    if (StringUtils.isBlank(optUserConnection.get().getAccessToken())) {
      throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "Refresh token not found");
    }
    String accessToken;
    if (!isConnectionAlive(principal)) {
      refreshToken(model, principal);
      optUserConnection = getExistingUserConnection(principal.getName());
    }
    accessToken = optUserConnection.get().getAccessToken();
    return accessToken;
  }

  private String getExistingRefreshToken(Principal principal) {
    Optional<UserConnection> optUserConnection = getExistingUserConnection(principal.getName());
    if (optUserConnection.isEmpty()) {
      throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "User connection not found");
    }
    if (StringUtils.isBlank(optUserConnection.get().getRefreshToken())) {
      throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "Refresh token not found");
    }
    return optUserConnection.get().getRefreshToken();
  }

  private HttpHeaders getHttpHeadersWithToken(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.setBearerAuth(accessToken);
    return headers;
  }

  private URL getCallbackUrl() throws MalformedURLException {
    if (StringUtils.isEmpty(callbackBaseUrl)) {
      return new URL(properties.getServerUrl());
    } else {
      return new URL(callbackBaseUrl);
    }
  }

  private AccessToken requestAccessToken(String authorizationCode) throws HttpStatusCodeException {
    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "authorization_code");
    formData.add("code", authorizationCode);
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
    headers.add("Accept", "*/*");
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));

    formData.add("client_id", clientId);
    formData.add("client_secret", clientSecret);
    formData.add("redirect_uri", URL_CALLBACK);
    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

    return restTemplate
        .exchange(URL_TOKEN_END_POINT, HttpMethod.POST, request, AccessToken.class)
        .getBody();
  }

  private long getExpireTime(Long expiresIn) {
    return Instant.now().toEpochMilli() + (expiresIn * 1000);
  }

  protected Optional<UserConnection> getExistingUserConnection(String username) {
    Optional<UserConnection> optConn =
        userConnectionManager.findByUserNameProviderName(username, DMPONLINE_APP_NAME);
    if (!optConn.isPresent()) {
      log.error("No DMPonline connection found for user {}", username);
    }
    return optConn;
  }

  private void createUserConnection(Principal principal, AccessToken accessToken) {
    UserConnection conn = new UserConnection();
    conn.setAccessToken(accessToken.getAccessToken());
    conn.setRefreshToken(accessToken.getRefreshToken());
    conn.setExpireTime(getExpireTime(accessToken.getExpiresIn()));
    conn.setDisplayName("DMPonline access token");
    conn.setId(
        new UserConnectionId(principal.getName(), DMPONLINE_APP_NAME, "ProviderUserIdNotNeeded"));
    userConnectionManager.save(conn);
  }
}
