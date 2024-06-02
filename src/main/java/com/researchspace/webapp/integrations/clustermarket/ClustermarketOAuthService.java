package com.researchspace.webapp.integrations.clustermarket;

import static com.researchspace.service.IntegrationsHandler.CLUSTERMARKET_APP_NAME;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserConnectionManager;
import java.security.Principal;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class ClustermarketOAuthService {
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
    private @JsonProperty("user") String username;
  }

  static final String CLUSTERMARKET_ACCESS_TOKEN_URL = "oauth/token";
  private final IPropertyHolder properties;

  @Value("${clustermarket.client.id}")
  private String clientId;

  @Value("${clustermarket.secret}")
  private String clientSecret;

  @Value("${clustermarket.web.url}")
  private String clustermarketWebUrl;

  private UserConnectionManager userConnectionManager;
  private RestTemplate restTemplate;

  public ClustermarketOAuthService(
      UserConnectionManager userConnectionManager, IPropertyHolder properties) {
    this.userConnectionManager = userConnectionManager;
    this.properties = properties;
    this.restTemplate = new RestTemplate();
  }

  /**
   * @param subject
   * @return the existing access token if it has not expired, else a refreshed access token
   */
  public String getExistingAccessTokenAndRefreshIfExpired(Principal subject) {
    Optional<UserConnection> optConn =
        userConnectionManager.findByUserNameProviderName(subject.getName(), CLUSTERMARKET_APP_NAME);
    if (optConn.isPresent()) {
      UserConnection conn = optConn.get();
      // refresh if within 60 seconds of expiry
      if (conn.getExpireTime().compareTo(Instant.now().toEpochMilli() + 60000) < 0) {
        // Set required post parameters
        Map<String, String> kv = new HashMap<>();
        setClientSecretId(kv);
        kv.put("refresh_token", conn.getRefreshToken());
        kv.put("grant_type", "refresh_token");

        ResponseEntity<AccessToken> newToken = getAccessToken(kv);
        saveUserConnection(conn, newToken);
        // save encrypts the token so return the unencrypted value from the response instead
        return newToken.getBody().getAccessToken();
      } else {
        // a token read from the database is decrypted
        return conn.getAccessToken();
      }
    } else {
      throw new NoTokenException();
    }
  }

  private void saveUserConnection(UserConnection conn, ResponseEntity<AccessToken> newToken) {
    conn.setAccessToken(newToken.getBody().getAccessToken());
    conn.setRefreshToken(newToken.getBody().getRefreshToken());
    conn.setExpireTime(getExpireTime(newToken));
    userConnectionManager.save(conn);
  }

  /**
   * @param authorizationCode
   * @return a new Access token using the authorisation code.
   */
  public void generateAndSaveAuthCodeAccessToken(String authorizationCode, User subject) {
    Map<String, String> kv = new HashMap<>();
    setClientSecretId(kv);
    kv.put("code", authorizationCode);
    kv.put("grant_type", "authorization_code");
    String redirectUri = properties.getServerUrl() + "/apps/clustermarket/redirect_uri";
    kv.put("redirect_uri", redirectUri);
    ResponseEntity<AccessToken> accessToken = getAccessToken(kv);
    log.info("Got access token {}", accessToken);
    UserConnection conn = new UserConnection();
    conn.setDisplayName("RSpace Clustermarket access token");
    conn.setId(
        new UserConnectionId(
            subject.getUsername(),
            CLUSTERMARKET_APP_NAME,
            accessToken.getBody().getUsername() != null
                ? accessToken.getBody().getUsername()
                : CLUSTERMARKET_APP_NAME));
    conn.setRank(1);
    saveUserConnection(conn, accessToken);
  }

  private ResponseEntity<AccessToken> getAccessToken(Map<String, String> kv) {
    HttpEntity<Map<String, String>> accessTokenRequestEntity =
        new HttpEntity<>(kv, getApiHeaders());
    log.info(
        "Sending access token request to: " + clustermarketWebUrl + CLUSTERMARKET_ACCESS_TOKEN_URL);
    for (String key : kv.keySet()) {
      log.info("Access token request had key " + key + " with value: " + kv.get(key));
    }
    ResponseEntity<AccessToken> accessToken =
        restTemplate.exchange(
            clustermarketWebUrl + CLUSTERMARKET_ACCESS_TOKEN_URL,
            HttpMethod.POST,
            accessTokenRequestEntity,
            AccessToken.class);
    if (!accessToken.hasBody()) {
      throw new IllegalStateException(
          String.format("Remote service returned corrupt access token"));
    }
    return accessToken;
  }

  private void setClientSecretId(Map<String, String> kv) {
    kv.put("client_id", clientId);
    kv.put("client_secret", clientSecret);
  }

  private long getExpireTime(ResponseEntity<AccessToken> accessToken) {
    return Instant.now().toEpochMilli() + (accessToken.getBody().getExpiresIn() * 1000);
  }

  private HttpHeaders getApiHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
    return headers;
  }
}
