package com.researchspace.webapp.integrations.dryad;

import static com.researchspace.service.IntegrationsHandler.DRYAD_APP_NAME;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.webapp.integrations.helper.BaseOAuth2Controller;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping("/apps/dryad")
@Slf4j
public class DryadOAuthController extends BaseOAuth2Controller {

  @Value("${dryad.client.id}")
  private String clientId;

  @Value("${dryad.client.secret}")
  private String clientSecret;

  @Value("${dryad.base.url}")
  private String baseUrl;

  private final RestTemplate restTemplate;

  public DryadOAuthController() {
    this.restTemplate = new RestTemplate();
  }

  /**
   * Connects the user to the Dryad service by redirecting to the Dryad OAuth page.
   *
   * @return RedirectView to the Dryad OAuth page.
   */
  @PostMapping("/connect")
  public RedirectView connect() {
    String redirectUrl =
        URLEncoder.encode(
            properties.getServerUrl() + "/apps/dryad/callback", StandardCharsets.UTF_8);
    String redirectPrams =
        String.format(
            "/oauth/authorize?client_id=%s&redirect_uri=%s&response_type=code&scope=all",
            clientId, redirectUrl);
    return new RedirectView(baseUrl + redirectPrams);
  }

  /** Deletes any current user connections to Dryad. */
  @DeleteMapping("/connect")
  public void disconnect(Principal principal) {
    int deleted =
        userConnectionManager.deleteByUserAndProvider(DRYAD_APP_NAME, principal.getName());
    log.info("Deleted {} Dryad connection(s) for user {}", deleted, principal.getName());
  }

  /**
   * Callback for the Dryad OAuth page.
   *
   * @param model the model
   * @return the dryad connected page
   */
  @GetMapping("/callback")
  public String callback(
      @RequestParam Map<String, String> params, Model model, Principal principal) {
    // Call dryad token endpoint to get access token
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
    map.add("grant_type", "authorization_code");
    map.add("client_id", clientId);
    map.add("client_secret", clientSecret);
    map.add("code", params.get("code"));
    map.add("redirect_uri", properties.getServerUrl() + "/apps/dryad/callback");

    HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(map, headers);
    try {
      ResponseEntity<DryadAccessToken> response =
          restTemplate.postForEntity(baseUrl + "/oauth/token", entity, DryadAccessToken.class);
      log.debug("Response: {}", response.getBody());
      log.debug("Response status: {}", response.getStatusCode());
      DryadAccessToken accessToken = response.getBody();
      log.debug("Got access token: " + accessToken.getAccessToken());
      UserConnection conn = new UserConnection();
      String accessTokenStr = accessToken.getAccessToken();
      conn.setAccessToken(accessTokenStr);
      conn.setExpireTime(accessToken.getExpiresIn());
      conn.setDisplayName("Dryad OAuth access token");
      long dryadUserId = getDryadUserId(accessTokenStr);
      conn.setId(new UserConnectionId(principal.getName(), DRYAD_APP_NAME, dryadUserId + ""));
      conn.setRank(1);
      // This is null for now as dryad doesn't currently provide a proper OAuth flow to refresh the
      // token.
      conn.setRefreshToken(null);
      log.debug("Plain text access token is {}", accessTokenStr);
      userConnectionManager.save(conn);

    } catch (HttpStatusCodeException e) {
      OauthAuthorizationError error =
          OauthAuthorizationError.builder()
              .appName("Dryad")
              .errorMsg("Exception during token exchange")
              .errorDetails(e.getResponseBodyAsString())
              .build();
      model.addAttribute("error", error);
      return "connect/authorizationError";
    }
    return "connect/dryad/connected";
  }

  private long getDryadUserId(String accessTokenStr) {
    String userEndpoint = baseUrl + "/api/v2/test";
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + accessTokenStr);
    ResponseEntity<DryadOAuthTest> response =
        restTemplate.exchange(
            userEndpoint, HttpMethod.GET, new HttpEntity<>(headers), DryadOAuthTest.class);
    log.info("Response from test endpoint: {}", response.getBody());
    DryadOAuthTest dryadOAuthTest = response.getBody();
    log.info("Dryad user id is: {}", dryadOAuthTest.getUserId());
    return dryadOAuthTest.getUserId();
  }

  @Data
  public static class DryadAccessToken {
    private @JsonProperty("access_token") String accessToken;
    private @JsonProperty("token_type") String type;
    private @JsonProperty("expires_in") Long expiresIn;
    private String scope;
    private @JsonProperty("created_at") Long createdAt;
    private String error;

    @JsonProperty("error_description")
    private String errorDescription;

    private String status;
  }

  @Data
  public static class DryadOAuthTest {
    private String message;

    @JsonProperty("user_id")
    private long userId;
  }
}
