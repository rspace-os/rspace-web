package com.researchspace.webapp.integrations.owncloud;

import static com.researchspace.service.IntegrationsHandler.OWNCLOUD_APP_NAME;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.webapp.integrations.helper.BaseOAuth2Controller;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError.OauthAuthorizationErrorBuilder;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpSession;
import lombok.Data;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
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

@Controller
@RequestMapping("/apps/owncloud")
public class OwnCloudController extends BaseOAuth2Controller {

  private static final String UTF_8 = "UTF-8";
  protected static final String SESSION_OWNCLOUD_USERNAME = "SESSION_OWNCLOUD_USERNAME";
  protected static final String SESSION_OWNCLOUD_PASSWORD = "SESSION_OWNCLOUD_PASSWORD";
  private static final String ERROR = "error";
  private static final String CONNECT_AUTHORIZATION_ERROR = "connect/authorizationError";
  private static final String ACCESS_TOKEN = "access_token";
  private static final String USERNAME = "username";

  @Value("${owncloud.url}")
  private String ownCloudBaseURL;

  @Value("${owncloud.client.id}")
  private String clientId;

  @Value("${owncloud.secret}")
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

  static class OwnCloudControllerConnector {
    String doRedirectCall(String ownCloudUrl, String clientId, String clientSecret)
        throws IOException {
      URL url = new URL(ownCloudUrl);
      HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
      urlConn.setRequestMethod("POST");

      // ownCloud OAuth token request uses basic auth with
      // client id as username and client secret as password
      // per documentation at https://github.com/owncloud/oauth2
      String userpass = clientId + ":" + clientSecret;
      String basicAuth =
          "Basic "
              + new String(
                  Base64.encodeBase64(userpass.getBytes(StandardCharsets.UTF_8)),
                  StandardCharsets.UTF_8);
      urlConn.setRequestProperty("Authorization", basicAuth);
      // rspac-2123
      urlConn.setRequestProperty("Content-Length", "0");

      return IOUtils.toString(
          new InputStreamReader(urlConn.getInputStream(), StandardCharsets.UTF_8));
    }

    String doRefreshCall(String ownCloudRefreshUrl, String clientId, String clientSecret)
        throws IOException {
      URL url = new URL(ownCloudRefreshUrl);
      HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
      urlConn.setRequestMethod("POST");
      // rspac-2123
      urlConn.setRequestProperty("Content-Length", "0");

      // ownCloud OAuth token request uses basic auth with
      // client id as username and client secret as password
      // per documentation at https://github.com/owncloud/oauth2
      String userpass = clientId + ":" + clientSecret;
      String basicAuth =
          "Basic "
              + new String(
                  Base64.encodeBase64(userpass.getBytes(StandardCharsets.UTF_8)),
                  StandardCharsets.UTF_8);
      urlConn.setRequestProperty("Authorization", basicAuth);

      return IOUtils.toString(
          new InputStreamReader(urlConn.getInputStream(), StandardCharsets.UTF_8));
    }
  }

  OwnCloudControllerConnector connector = new OwnCloudControllerConnector();

  @GetMapping("/redirect_uri")
  public String handleOwnCloudRedirect(
      @RequestParam Map<String, String> params, Model model, HttpSession session) {
    // param code or error
    if (params.containsKey(ERROR)) {
      OauthAuthorizationError error =
          getAuthErrorBuilder()
              .errorMsg("Error connecting to ownCloud")
              .errorDetails(params.get(ERROR))
              .build();

      model.addAttribute(ERROR, error);
      return CONNECT_AUTHORIZATION_ERROR;
    }

    if (userManager.getAuthenticatedUserInSession() == null) {
      OauthAuthorizationError error =
          getAuthErrorBuilder()
              .errorMsg("Error connecting to ownCloud")
              .errorDetails(params.get(ERROR))
              .build();

      model.addAttribute(ERROR, error);
      return CONNECT_AUTHORIZATION_ERROR;
    }

    String authorizationCode = params.get("code");

    try {
      String ownCloudRedirect = properties.getServerUrl() + "/owncloud/redirect_uri";
      String ownCloudUrl =
          ownCloudBaseURL
              + "/index.php/apps/oauth2/api/v1/token?grant_type=authorization_code&code="
              + authorizationCode
              + "&redirect_uri="
              + URLEncoder.encode(ownCloudRedirect, UTF_8);

      String content = connector.doRedirectCall(ownCloudUrl, clientId, clientSecret);

      ObjectMapper mapper = new ObjectMapper();
      mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      Map<String, String> contentMap =
          mapper.readValue(content, new TypeReference<Map<String, String>>() {});

      User subject = userManager.getAuthenticatedUserInSession();

      UserConnection conn = new UserConnection();
      conn.setAccessToken(contentMap.get(ACCESS_TOKEN));
      conn.setExpireTime(
          Instant.now().toEpochMilli() + (Long.parseLong(contentMap.get("expires_in")) * 1000));
      conn.setDisplayName("RSpace ownCloud access token");
      conn.setId(
          new UserConnectionId(
              subject.getUsername(), OWNCLOUD_APP_NAME, contentMap.get("user_id")));
      conn.setRank(1);
      conn.setRefreshToken(contentMap.get("refresh_token"));
      conn.setSecret(clientSecret);

      userConnectionManager.save(conn);
      model.addAttribute("ownCloudAccessToken", contentMap.get(ACCESS_TOKEN));
      model.addAttribute("ownCloudUsername", conn.getId().getProviderUserId());

      log.info("ownCloud response retrieved fine");

    } catch (IOException e) {
      log.warn("io exception on contacting oauth.access url", e);
      OauthAuthorizationError error =
          getAuthErrorBuilder()
              .errorMsg("exception during token exchange")
              .errorDetails(e.getMessage())
              .build();

      model.addAttribute(ERROR, error);
      return CONNECT_AUTHORIZATION_ERROR;
    }

    return "connect/owncloud/connected";
  }

  private OauthAuthorizationErrorBuilder getAuthErrorBuilder() {
    return OauthAuthorizationError.builder().appName("ownCloud");
  }

  /**
   * Gets the access token id there is one, else returns empty string.
   *
   * @param subject
   * @return
   */
  @GetMapping("/accessCredentials")
  public @ResponseBody Map<String, String> getAccessCredentials(Principal subject) {
    Optional<UserConnection> connection =
        userConnectionManager.findByUserNameProviderName(subject.getName(), OWNCLOUD_APP_NAME);

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

  @GetMapping("/refreshToken")
  public @ResponseBody Map<String, String> refreshAccessCredentials(Principal subject) {
    Optional<UserConnection> connectionOption =
        userConnectionManager.findByUserNameProviderName(subject.getName(), OWNCLOUD_APP_NAME);

    if (connectionOption.isPresent()) {
      UserConnection connection = connectionOption.get();
      String refreshToken = connection.getRefreshToken();

      // Call ownCloud server to refresh token
      String ownCloudRefreshUrl =
          ownCloudBaseURL
              + "/index.php/apps/oauth2/api/v1/token?grant_type=refresh_token&refresh_token="
              + refreshToken;

      try {
        String content = connector.doRefreshCall(ownCloudRefreshUrl, clientId, clientSecret);

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
        log.warn("io exception on contacting ownCloud oauth refresh token url", e);
        return null;
      }
    } else {
      return null;
    }
  }

  @GetMapping("/sessionInfo")
  @ResponseBody
  public Map<String, String> getOwnCloudCredentialsFromSession(HttpSession session) {
    HashMap<String, String> map = new HashMap<>();
    map.put(USERNAME, (String) session.getAttribute(SESSION_OWNCLOUD_USERNAME));
    map.put("password", (String) session.getAttribute(SESSION_OWNCLOUD_PASSWORD));
    return map;
  }

  @PostMapping("/sessionInfo")
  @ResponseBody
  public String saveOwnCloudCredentialsToSession(
      @RequestParam(USERNAME) String username,
      @RequestParam("password") String password,
      HttpSession session) {
    session.setAttribute(SESSION_OWNCLOUD_USERNAME, username);
    session.setAttribute(SESSION_OWNCLOUD_PASSWORD, password);
    return "OK";
  }

  @GetMapping("/redirectLink")
  @ResponseBody
  public ModelAndView redirectLink(@RequestParam("path") String path)
      throws UnsupportedEncodingException {
    String decodedPath = URLDecoder.decode(path, UTF_8);
    String parentPath = decodedPath.substring(0, decodedPath.lastIndexOf("/"));
    String redirectURL = ownCloudBaseURL + "/index.php/apps/files/?dir=" + parentPath;

    return new ModelAndView("redirect:" + redirectURL);
  }

  public void removeOwnCloudCredentialsFromSession(HttpSession session) {
    log.info("removing ownCloud credentials from session");
    session.removeAttribute(SESSION_OWNCLOUD_USERNAME);
    session.removeAttribute(SESSION_OWNCLOUD_PASSWORD);
  }

  /**
   * Generates connection URL to connect to authorisation endpoint of service
   *
   * @return
   */
  @PostMapping("/connect")
  public RedirectView connect() {
    String redirectUrl = properties.getServerUrl() + "/owncloud/redirect_uri";
    String authURL =
        String.format(
            "%s/index.php/apps/oauth2/authorize?response_type=code&client_id=%s&redirect_uri=%s",
            ownCloudBaseURL, clientId, redirectUrl);
    return new RedirectView(authURL);
  }

  @DeleteMapping("/connect")
  public void disconnect(Principal principal) {
    int deleted =
        userConnectionManager.deleteByUserAndProvider(OWNCLOUD_APP_NAME, principal.getName());
    log.info("Deleted {} owncloud connection(s) for user {}", deleted, principal.getName());
  }

  void setUserConnectionManager(UserConnectionManager manager) {
    this.userConnectionManager = manager;
  }
}
