package com.researchspace.webapp.integrations.protocolsio;

import static com.researchspace.service.IntegrationsHandler.PROTOCOLS_IO_APP_NAME;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.protocolsio.PIOUser;
import com.researchspace.webapp.integrations.helper.BaseOAuth2Controller;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError;
import java.security.Principal;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping("/apps/protocolsio")
public class ProtocolsIO_OAuthController extends BaseOAuth2Controller {

  @Value("${protocolsio.client.id}")
  private String clientId;

  @Value("${protocolsio.secret}")
  private String clientSecret;

  static final String PROTOCOLSIO_ACCESS_TOKEN_URL = "https://www.protocols.io/api/v3/oauth/token";
  static final String PROTOCOLSIO_AUTH_URL = "https://www.protocols.io/api/v3/oauth/authorize";
  static final int REFRESH_TOKEN_EXPIRED_CODE = 1217;

  private RestTemplate restTemplate;

  public ProtocolsIO_OAuthController() {
    HttpComponentsClientHttpRequestFactory gzipSupportingRequestFactory =
        new HttpComponentsClientHttpRequestFactory(HttpClientBuilder.create().build());
    restTemplate = new RestTemplate(gzipSupportingRequestFactory);
  }

  /**
   * Generates connection URL to connect to authorisation endpoint of service
   *
   * @return
   */
  @PostMapping("/connect")
  public RedirectView connect() {
    String redirectUrl = properties.getServerUrl() + "/apps/protocolsio/redirect_uri";
    String state = generateState();
    String url =
        String.format(
            "%s?client_id=%s&redirect_url=%s&response_type=%s&scope=readwrite&state=%s",
            PROTOCOLSIO_AUTH_URL, clientId, redirectUrl, "code", state);
    return new RedirectView(url);
  }

  @DeleteMapping("/connect")
  public void disconnect(Principal principal) {
    int deleted =
        userConnectionManager.deleteByUserAndProvider(PROTOCOLS_IO_APP_NAME, principal.getName());
    log.info("Deleted {} ProtocolsIO connection(s) for user {}", deleted, principal.getName());
  }

  @Data
  public static class AccessToken {
    private @JsonProperty("access_token") String accessToken;
    private String scope;
    private @JsonProperty("token_type") String type;
    private String error;
    private @JsonProperty("refresh_token") String refreshToken;
    private @JsonProperty("refresh_expires_in") Long refreshExpiresIn;
    private @JsonProperty("expires_in") Long expiresIn;
    private @JsonProperty("status_code") Long statusCode;
    // this is not set for refresh token
    private PIOUser user;
  }

  /**
   * Gets the access token id there is one, else returns empty string.
   *
   * @param subject
   * @return
   */
  @GetMapping("/accessToken")
  public @ResponseBody String getAccessToken(Principal subject) {
    return doGetAccessToken(subject, PROTOCOLS_IO_APP_NAME);
  }

  @GetMapping("/redirect_uri")
  public String onAuthorization(
      @RequestParam Map<String, String> params, Model model, HttpServletRequest request) {
    User subject = userManager.getAuthenticatedUserInSession();
    verifyStateParameter(request);

    String authorizationCode = params.get("code");
    try {
      ResponseEntity<AccessToken> accessToken = getAccessToken(authorizationCode);
      log.info("Got access token {}", accessToken);
      UserConnection conn = new UserConnection();
      conn.setAccessToken(accessToken.getBody().getAccessToken());
      conn.setExpireTime(getExpireTime(accessToken));
      conn.setDisplayName("RSpace protocolsIO access token");
      conn.setId(
          new UserConnectionId(
              subject.getUsername(),
              PROTOCOLS_IO_APP_NAME,
              accessToken.getBody().getUser().getUsername()));
      conn.setRank(1);
      conn.setRefreshToken(accessToken.getBody().getRefreshToken());
      userConnectionManager.save(conn);
      model.addAttribute("pioAccessToken", conn.getAccessToken());
      return "connect/protocolsio/connected";
    } catch (HttpStatusCodeException e) {
      OauthAuthorizationError error =
          OauthAuthorizationError.builder()
              .appName("Protocols.io")
              .errorMsg("Exception during token exchange")
              .errorDetails(e.getResponseBodyAsString())
              .build();
      model.addAttribute("error", error);
      return "connect/authorizationError";
    }
  }

  private long getExpireTime(ResponseEntity<AccessToken> accessToken) {
    return Instant.now().toEpochMilli() + (accessToken.getBody().getExpiresIn() * 1000);
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ClientError {
    @JsonProperty("status_code")
    private int statusCode;

    @JsonProperty("error_message")
    private String errorMessage;
  }

  @PostMapping("/refreshToken")
  public @ResponseBody ResponseEntity<String> refreshToken(Principal subject) {
    Optional<UserConnection> optConn =
        userConnectionManager.findByUserNameProviderName(subject.getName(), PROTOCOLS_IO_APP_NAME);
    if (!optConn.isPresent()) {
      return new ResponseEntity<String>(
          "No token entry, please authenticate", HttpStatus.BAD_REQUEST);
    }

    UserConnection conn = optConn.get();
    // Set required post parameters
    Map<String, String> kv = new HashMap<>();
    setClientSecretId(kv);
    kv.put("refresh_token", conn.getRefreshToken());
    kv.put("grant_type", "refresh_token");
    HttpEntity<Map<String, String>> accessTokenRequestEntity =
        new HttpEntity<>(kv, getApiHeaders());
    try {
      ResponseEntity<AccessToken> newToken =
          restTemplate.exchange(
              PROTOCOLSIO_ACCESS_TOKEN_URL,
              HttpMethod.POST,
              accessTokenRequestEntity,
              AccessToken.class);
      // refresh token is also updated
      conn.setAccessToken(newToken.getBody().getAccessToken());
      conn.setRefreshToken(newToken.getBody().getRefreshToken());
      conn.setExpireTime(getExpireTime(newToken));
      userConnectionManager.save(conn);
      return new ResponseEntity<String>(conn.getAccessToken(), HttpStatus.OK);
    } catch (HttpStatusCodeException e) {
      // response statuses returned to RSpace are from RSpace users point of view, not PIO client
      // point of view.
      log.warn(e.getMessage());
      ClientError err = JacksonUtil.fromJson(e.getResponseBodyAsString(), ClientError.class);
      if (err != null) {
        log.warn(err.getErrorMessage());
        switch (err.getStatusCode()) {
          case REFRESH_TOKEN_EXPIRED_CODE:
            log.warn(
                " Refresh token expired for {}:{}",
                subject.getName(),
                StringUtils.abbreviate(conn.getRefreshToken(), 5));
            return new ResponseEntity<String>(
                "Refresh token expired", HttpStatus.SERVICE_UNAVAILABLE);
          default:
            log.warn(
                " General error refreshing token for {}:{}",
                subject.getName(),
                err.getErrorMessage());
            return new ResponseEntity<String>("General error ", HttpStatus.INTERNAL_SERVER_ERROR);
        }
      }
      log.warn(
          " General error refreshing token for {}: {}",
          subject.getName(),
          e.getResponseBodyAsString());
      return new ResponseEntity<String>("Unknown error", HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  private ResponseEntity<AccessToken> getAccessToken(String authorizationCode) {
    // Set required post parameters
    Map<String, String> kv = new HashMap<>();
    setClientSecretId(kv);
    kv.put("code", authorizationCode);
    kv.put("grant_type", "authorization_code");

    HttpEntity<Map<String, String>> accessTokenRequestEntity =
        new HttpEntity<>(kv, getApiHeaders());

    ResponseEntity<AccessToken> accessToken =
        restTemplate.exchange(
            PROTOCOLSIO_ACCESS_TOKEN_URL,
            HttpMethod.POST,
            accessTokenRequestEntity,
            AccessToken.class);
    return accessToken;
  }

  private void setClientSecretId(Map<String, String> kv) {
    kv.put("client_id", clientId);
    kv.put("client_secret", clientSecret);
  }

  private HttpHeaders getApiHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
    return headers;
  }
}
