package com.researchspace.webapp.integrations.figshare;

import static com.researchspace.service.IntegrationsHandler.FIGSHARE_APP_NAME;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.figshare.api.Figshare;
import com.researchspace.figshare.connect.FigshareOAuth2Endpoints;
import com.researchspace.figshare.impl.FigshareTemplate;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.webapp.integrations.helper.BaseOAuth2Controller;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;

/** Class responsible for handling connection between RSpace and GitHub */
@Controller
@RequestMapping("/apps/figshare")
@Slf4j
public class FigshareOAuthController extends BaseOAuth2Controller {

  @Value("${figshare.id}")
  private String clientId;

  @Value("${figshare.secret}")
  private String clientSecret;

  private RestTemplate restTemplate;

  public FigshareOAuthController() {
    this.restTemplate = new RestTemplate();
  }

  /**
   * Generates connection URL to connect to authorisation endpoint of service
   *
   * @return
   */
  @PostMapping("/connect")
  public RedirectView connect() {
    String redirectUrl =
        URLEncoder.encode(
            properties.getServerUrl() + "/apps/figshare/redirect_uri", StandardCharsets.UTF_8);
    String state = generateState();
    log.info("redirect URI is {}", redirectUrl);
    String url =
        String.format(
            "%s?client_id=%s&redirect_uri=%s&response_type=%s&scope=all&state=%s",
            FigshareOAuth2Endpoints.AUTHENTICATE_URL, clientId, redirectUrl, "code", state);
    log.info("Auth post is {}", url);
    return new RedirectView(url);
  }

  @DeleteMapping("/connect")
  public void disconnect(Principal principal) {
    int deleted =
        userConnectionManager.deleteByUserAndProvider(
            IntegrationsHandler.FIGSHARE_APP_NAME, principal.getName());
    log.info("Deleted {} Figshare connection(s) for user {}", deleted, principal.getName());
  }

  @Data
  public static class AccessToken {
    private @JsonProperty("access_token") String accessToken;
    private String scope;
    private @JsonProperty("token_type") String type;
    private String error;
    private @JsonProperty("refresh_token") String refreshToken;
    private @JsonProperty("expires_in") Long expiresIn;
  }

  /**
   * Callback after authentication, with code that can be used to create an access token. Access
   * tokens appear to last for 1 year.
   *
   * @param params
   * @param model
   * @param request
   * @return
   */
  @GetMapping("/redirect_uri")
  public String onAuthorization(
      @RequestParam Map<String, String> params, Model model, HttpServletRequest request) {
    User subject = userManager.getAuthenticatedUserInSession();
    verifyStateParameter(request);

    String authorizationCode = params.get("code");
    try {
      ResponseEntity<FigshareOAuthController.AccessToken> accessToken =
          getAccessToken(authorizationCode);
      log.info("Got access token {}", accessToken);
      UserConnection conn = new UserConnection();
      AccessToken token = accessToken.getBody();
      String accessTokenStr = token.getAccessToken();
      long id = extractIdFromAccount(accessTokenStr);
      conn.setAccessToken(accessTokenStr);
      conn.setExpireTime(getExpireTime(accessToken));
      conn.setDisplayName("RSpace Figshare access token");
      conn.setId(new UserConnectionId(subject.getUsername(), FIGSHARE_APP_NAME, id + ""));
      conn.setRank(1);
      conn.setRefreshToken(token.getRefreshToken());
      log.info("Plain text access token is {}", accessTokenStr);
      userConnectionManager.save(conn);
      return "connect/figshare/connected";
    } catch (HttpStatusCodeException e) {
      OauthAuthorizationError error =
          OauthAuthorizationError.builder()
              .appName("Figshare")
              .errorMsg("Exception during token exchange")
              .errorDetails(e.getResponseBodyAsString())
              .build();
      model.addAttribute("error", error);
      return "connect/authorizationError";
    }
  }

  long extractIdFromAccount(String accessTokenStr) {
    Figshare figshare = new FigshareTemplate(accessTokenStr);
    return figshare.account().getId();
  }

  private long getExpireTime(ResponseEntity<FigshareOAuthController.AccessToken> accessToken) {
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

  private ResponseEntity<AccessToken> getAccessToken(String authorizationCode) {
    // Set required post parameters
    Map<String, String> kv = new HashMap<>();

    kv.put("code", authorizationCode);
    kv.put("grant_type", "authorization_code");
    kv.put("client_id", clientId);
    kv.put("client_secret", clientSecret);

    HttpEntity<Map<String, String>> accessTokenRequestEntity =
        new HttpEntity<>(kv, getApiHeaders());

    return requestAccessToken(accessTokenRequestEntity);
  }

  ResponseEntity<AccessToken> requestAccessToken(
      HttpEntity<Map<String, String>> accessTokenRequestEntity) {
    return restTemplate.exchange(
        FigshareOAuth2Endpoints.TOKEN_URL,
        HttpMethod.POST,
        accessTokenRequestEntity,
        AccessToken.class);
  }

  private HttpHeaders getApiHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    return headers;
  }
}
