package com.researchspace.webapp.integrations.clustermarket;

import static com.researchspace.service.IntegrationsHandler.CLUSTERMARKET_APP_NAME;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.model.User;
import com.researchspace.webapp.integrations.helper.BaseOAuth2Controller;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError;
import java.security.Principal;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.servlet.view.RedirectView;

@Slf4j
@Controller
@RequestMapping("/apps/clustermarket")
public class ClustermarketOAuthController extends BaseOAuth2Controller {

  static final String CLUSTERMARKET_AUTH_URL = "oauth/authorize";
  private final ClustermarketOAuthService clustermarketOAuthService;

  @Value("${clustermarket.client.id}")
  private String clientId;

  @Value("${clustermarket.web.url}")
  private String clustermarketWebUrl;

  public ClustermarketOAuthController(ClustermarketOAuthService clustermarketOAuthService) {
    this.clustermarketOAuthService = clustermarketOAuthService;
  }

  /**
   * Generates connection URL to connect to authorisation endpoint of service
   *
   * @return
   */
  @PostMapping("/connect")
  public RedirectView connect() {
    String state = generateState();
    String redirectUri = properties.getServerUrl() + "/apps/clustermarket/redirect_uri";
    String url =
        String.format(
            "%s?client_id=%s&redirect_uri=%s&response_type=%s&scope=read_equipment+read_bookings+read_accounts&state=%s",
            clustermarketWebUrl + CLUSTERMARKET_AUTH_URL, clientId, redirectUri, "code", state);
    return new RedirectView(url);
  }

  @DeleteMapping("/connect")
  public void disconnect(Principal principal) {
    int deleted =
        userConnectionManager.deleteByUserAndProvider(CLUSTERMARKET_APP_NAME, principal.getName());
    log.info("Deleted {} Clustermarket connection(s) for user {}", deleted, principal.getName());
  }

  /**
   * Gets the access token id there is one, else returns empty string.
   *
   * @param subject
   * @return
   */
  @GetMapping("/accessToken")
  public @ResponseBody String getAccessToken(Principal subject) {
    return doGetAccessToken(subject, CLUSTERMARKET_APP_NAME);
  }

  @GetMapping("/redirect_uri")
  public String onAuthorization(
      @RequestParam Map<String, String> params, Model model, HttpServletRequest request) {
    User subject = userManager.getAuthenticatedUserInSession();
    verifyStateParameter(request);
    String authorizationCode = params.get("code");
    try {
      clustermarketOAuthService.generateAndSaveAuthCodeAccessToken(authorizationCode, subject);
      return "connect/clustermarket/connected";
    } catch (HttpStatusCodeException e) {
      log.error(makeMessage(e), e);
      OauthAuthorizationError error =
          OauthAuthorizationError.builder()
              .appName(CLUSTERMARKET_APP_NAME)
              .errorMsg("Exception during token exchange")
              .errorDetails(e.getResponseBodyAsString())
              .build();
      model.addAttribute("error", error);
      return "connect/authorizationError";
    }
  }

  private String makeMessage(Exception exception) {
    String message = exception.getMessage();
    Throwable cause = exception.getCause();
    if (cause != null) {
      message += " " + cause.getMessage();
    }
    return message;
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
}
