package com.researchspace.webapp.integrations.nextcloud;

import static com.researchspace.service.IntegrationsHandler.NEXTCLOUD_APP_NAME;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.service.ClientReadableSecret;
import com.researchspace.webapp.controller.IgnoreInLoggingInterceptor;
import com.researchspace.webapp.integrations.helper.BaseOAuth2Controller;
import com.researchspace.webapp.integrations.helper.ConnectionResultPage;
import com.researchspace.webapp.integrations.helper.OAuthTokenPost;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError.OauthAuthorizationErrorBuilder;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

/** Class responsible for handling connection between RSpace and Slack */
@Controller
@RequestMapping("/apps/nextcloud")
public class NextCloudController extends BaseOAuth2Controller {
  private static final String ERROR = "error";
  private static final String ACCESS_TOKEN = "access_token";
  private static final String USERNAME = "username";

  @Value("${nextcloud.url}")
  private String nextCloudBaseURL;

  @Value("${nextcloud.client.id}")
  private String clientId;

  @Value("${nextcloud.secret}")
  private String clientSecret;

  @Data
  public static class AccessToken {
    private @JsonProperty(ACCESS_TOKEN) String token;
    private String scope;
    private @JsonProperty("token_type") String type;
    private String error;
    private @JsonProperty("refresh_token") String refreshToken;
    private @JsonProperty("refresh_expires_in") Long refreshExpiresIn;
    private @JsonProperty("expires_in") Long expiresIn;
    private @JsonProperty("status_code") Long statusCode;
  }

  static class NextCloudControllerConnector {
    String doRedirectCall(String nextCloudUrl, String clientId, String clientSecret)
        throws IOException {
      return OAuthTokenPost.exchange(nextCloudUrl, clientId, clientSecret);
    }

    String doRefreshCall(String nextCloudRefreshUrl, String clientId, String clientSecret)
        throws IOException {
      return doRedirectCall(nextCloudRefreshUrl, clientId, clientSecret);
    }
  }

  NextCloudControllerConnector connector = new NextCloudControllerConnector();

  @GetMapping("/redirect_uri")
  @IgnoreInLoggingInterceptor(ignoreAllRequestParams = true)
  public String handleNextCloudRedirect(@RequestParam Map<String, String> params, Model model) {
    try {
      verifyStateParameter(params.get("state"));
    } catch (IllegalStateException invalidState) {
      log.warn("Nextcloud OAuth state mismatch");
      OauthAuthorizationError error =
          OauthAuthorizationError.builder()
              .appName("Nextcloud")
              .errorMsg(getText("apps.oauth.errors.connection", new Object[] {"Nextcloud"}))
              .errorDetails(invalidState.getMessage())
              .build();
      ConnectionResultPage.addError(
          model, "Nextcloud", "rspace.apps.nextcloud.connection", "NEXTCLOUD_CONNECTED", error);
      return ConnectionResultPage.VIEW;
    }

    // param code or error
    if (params.containsKey(ERROR)) {
      OauthAuthorizationError error =
          getAuthErrorBuilder()
              .errorMsg(getText("apps.oauth.errors.connection", new Object[] {"Nextcloud"}))
              .errorDetails(params.get(ERROR))
              .build();

      ConnectionResultPage.addError(
          model, "Nextcloud", "rspace.apps.nextcloud.connection", "NEXTCLOUD_CONNECTED", error);
      return ConnectionResultPage.VIEW;
    }

    if (userManager.getAuthenticatedUserInSession() == null) {
      OauthAuthorizationError error =
          getAuthErrorBuilder()
              .errorMsg(getText("apps.oauth.errors.connection", new Object[] {"Nextcloud"}))
              .errorDetails(params.get(ERROR))
              .build();

      ConnectionResultPage.addError(
          model, "Nextcloud", "rspace.apps.nextcloud.connection", "NEXTCLOUD_CONNECTED", error);
      return ConnectionResultPage.VIEW;
    }

    String authorizationCode = params.get("code");

    try {
      String nextCloudRedirect = properties.getServerUrl() + "/nextcloud/redirect_uri";
      String nextCloudUrl =
          nextCloudBaseURL
              + "/index.php/apps/oauth2/api/v1/token?grant_type=authorization_code&code="
              + URLEncoder.encode(authorizationCode, StandardCharsets.UTF_8)
              + "&redirect_uri="
              + URLEncoder.encode(nextCloudRedirect, StandardCharsets.UTF_8);

      String content = connector.doRedirectCall(nextCloudUrl, clientId, clientSecret);

      ObjectMapper mapper = new ObjectMapper();
      mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      Map<String, String> contentMap = mapper.readValue(content, new TypeReference<>() {});

      User subject = userManager.getAuthenticatedUserInSession();

      UserConnection conn = new UserConnection();
      conn.setAccessToken(contentMap.get(ACCESS_TOKEN));
      conn.setExpireTime(
          Instant.now().toEpochMilli() + (Long.parseLong(contentMap.get("expires_in")) * 1000));
      conn.setDisplayName("RSpace NextCloud access token");
      conn.setId(
          new UserConnectionId(
              subject.getUsername(), NEXTCLOUD_APP_NAME, contentMap.get("user_id")));
      conn.setRank(1);
      conn.setRefreshToken(contentMap.get("refresh_token"));
      conn.setSecret(clientSecret);

      userConnectionManager.save(conn);

      log.info("NextCloud response retrieved");

    } catch (IOException e) {
      log.warn("OAuth token exchange failed ({})", e.getClass().getSimpleName());
      OauthAuthorizationError error =
          getAuthErrorBuilder().errorMsg(getText("apps.oauth.errors.tokenExchange")).build();

      ConnectionResultPage.addError(
          model, "Nextcloud", "rspace.apps.nextcloud.connection", "NEXTCLOUD_CONNECTED", error);
      return ConnectionResultPage.VIEW;
    }

    ConnectionResultPage.addConnectionAttributes(
        model, "Nextcloud", "rspace.apps.nextcloud.connection", "NEXTCLOUD_CONNECTED");
    return ConnectionResultPage.VIEW;
  }

  private OauthAuthorizationErrorBuilder getAuthErrorBuilder() {
    return OauthAuthorizationError.builder().appName("nextCloud");
  }

  @ClientReadableSecret("the Nextcloud TinyMCE plugin calls Nextcloud from the browser")
  @GetMapping("/accessCredentials")
  public @ResponseBody Map<String, String> getAccessCredentials(Principal subject) {
    Optional<UserConnection> connection =
        userConnectionManager.findByUserNameProviderName(subject.getName(), NEXTCLOUD_APP_NAME);

    if (connection.isPresent()) {
      Map<String, String> response = new HashMap<>();
      response.put(ACCESS_TOKEN, connection.get().getAccessToken());
      response.put(USERNAME, connection.get().getId().getProviderUserId());
      response.put("expire_time", Long.toString(connection.get().getExpireTime()));

      return response;
    } else {
      return null;
    }
  }

  @ClientReadableSecret("the Nextcloud TinyMCE plugin calls Nextcloud from the browser")
  @GetMapping("/refreshToken")
  public @ResponseBody Map<String, String> refreshAccessCredentials(Principal subject) {
    Optional<UserConnection> connectionOption =
        userConnectionManager.findByUserNameProviderName(subject.getName(), NEXTCLOUD_APP_NAME);

    if (connectionOption.isPresent()) {
      UserConnection connection = connectionOption.get();
      String refreshToken = connection.getRefreshToken();

      String nextCloudRefreshUrl =
          nextCloudBaseURL
              + "/index.php/apps/oauth2/api/v1/token?grant_type=refresh_token&refresh_token="
              + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8);

      try {
        String content = connector.doRefreshCall(nextCloudRefreshUrl, clientId, clientSecret);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Map<String, String> contentMap =
            mapper.readValue(content, new TypeReference<Map<String, String>>() {});

        String newExpiresIn = contentMap.get("expires_in");
        String newAccessToken = contentMap.get(ACCESS_TOKEN);
        String newRefreshToken = contentMap.get("refresh_token");

        // Save the updated connection info
        connection.setAccessToken(newAccessToken);
        connection.setExpireTime(
            Instant.now().toEpochMilli() + (Long.parseLong(newExpiresIn) * 1000));
        connection.setRefreshToken(newRefreshToken);

        userConnectionManager.save(connection);

        // Return the applicable fields to the client
        Map<String, String> response = new HashMap<>();
        response.put(ACCESS_TOKEN, contentMap.get(ACCESS_TOKEN));
        response.put(USERNAME, connection.getId().getProviderUserId());
        response.put("expire_time", Long.toString(connection.getExpireTime()));

        return response;
      } catch (IOException e) {
        log.warn(
            "io exception on contacting nextcloud oauth refresh token url" + " ({})",
            e.getClass().getSimpleName());
        return null;
      }
    } else {
      return null;
    }
  }

  @GetMapping("/redirectLink")
  @ResponseBody
  public ModelAndView redirectLink(@RequestParam("path") String path)
      throws UnsupportedEncodingException {
    String fileIDSep = "__&&__";
    String fileID = "";
    if (path.indexOf(fileIDSep) != -1) {
      fileID = path.split(fileIDSep)[1];
      path = path.split("__&&__")[0];
    }
    String decodedPath = URLDecoder.decode(path, UTF_8);
    String parentPath = decodedPath.substring(0, decodedPath.lastIndexOf('/'));
    if (StringUtils.isBlank(parentPath)) {
      parentPath = "/";
    }
    String redirectURL = nextCloudBaseURL + "/index.php/apps/files/?dir=" + parentPath;
    redirectURL +=
        ("&openfile="
            + fileID
            + "&scrollto="
            + URLEncoder.encode(StringUtils.removeStart(path, parentPath).replace("/", "")));
    return new ModelAndView("redirect:" + redirectURL);
  }

  /**
   * Generates connection URL to connect to authorisation endpoint of service
   *
   * @return
   */
  @PostMapping("/connect")
  public RedirectView connect() {
    String redirectUrl = properties.getServerUrl() + "/nextcloud/redirect_uri";
    String authURL =
        String.format(
            "%s/index.php/apps/oauth2/authorize?response_type=code&client_id=%s&redirect_uri=%s",
            nextCloudBaseURL, clientId, redirectUrl);
    return new RedirectView(authURL + "&state=" + generateState());
  }

  @DeleteMapping("/connect")
  public void disconnect(Principal principal) {
    int deleted =
        userConnectionManager.deleteByUserAndProvider(principal.getName(), NEXTCLOUD_APP_NAME);
    log.info("Deleted {} nextcloud connection(s) for user {}", deleted, principal.getName());
  }
}
