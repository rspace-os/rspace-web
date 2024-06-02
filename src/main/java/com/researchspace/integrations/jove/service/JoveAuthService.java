package com.researchspace.integrations.jove.service;

import static com.researchspace.service.IntegrationsHandler.JOVE_APP_NAME;

import com.researchspace.integrations.jove.model.JoveToken;
import com.researchspace.model.User;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.UserConnectionManager;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
@Data
public class JoveAuthService {

  @Autowired private UserConnectionManager userConnectionManager;

  @Autowired private IntegrationsHandler integrationsHandler;

  @Autowired private IPropertyHolder propertyHolder;

  private RestTemplate restTemplate = new RestTemplate();

  private Map<UserConnection, JoveToken> tokens = new HashMap<>();

  public JoveToken getTokenAndRefreshIfExpired(User user) {
    // Get the user connection for jove
    JoveToken result = null;
    Optional<UserConnection> joveUserConn =
        userConnectionManager.findByUserNameProviderName(user.getUsername(), JOVE_APP_NAME);
    if (joveUserConn.isPresent()) {
      result = checkCurrentTokenRefreshIfNeeded(joveUserConn.get());
    } else {
      // There will be a case on community where Jove is enabled by default but user hasn't enabled
      // it
      // so we have to create a new UserConnection object and update the integration to enabled in
      // the database
      IntegrationInfo joveIntegration = integrationsHandler.getIntegration(user, JOVE_APP_NAME);
      joveIntegration.setEnabled(true);
      integrationsHandler.updateIntegrationInfo(user, joveIntegration);
      Optional<UserConnection> newJoveUserConn =
          userConnectionManager.findByUserNameProviderName(user.getUsername(), JOVE_APP_NAME);
      if (newJoveUserConn.isPresent()) {
        result = checkCurrentTokenRefreshIfNeeded(newJoveUserConn.get());
      } else {
        throw new JoveTokenException(
            "Error Retrieving Jove User Connection for user:" + user.getUsername());
      }
    }
    return result;
  }

  private JoveToken checkCurrentTokenRefreshIfNeeded(UserConnection userConnection) {
    // If token isn't null and hasn't expired we have a valid token so just return it.
    JoveToken userToken = tokens.get(userConnection);
    if (userToken != null && !hasJoveTokenExpired(userToken)) {
      log.debug("Current token is valid");
      return userToken;
    } else {
      // Try to remove token as it will have expired
      tokens.remove(userConnection);
      return generateNewJoveToken(userConnection);
    }
  }

  private boolean hasJoveTokenExpired(JoveToken token) {
    // Jove token expire time in seconds so need to convert to millis
    Long expireTimeMillis = token.getExpires() * 1000L;
    long nowPlusSixtySec = Instant.now().toEpochMilli() + 60000;
    return expireTimeMillis.compareTo(nowPlusSixtySec) < 0;
  }

  private JoveToken generateNewJoveToken(UserConnection userConnection) {
    ResponseEntity<JoveToken> joveTokenResponseEntity =
        retrieveNewTokenViaJoveApi(userConnection.getAccessToken());
    JoveToken token = joveTokenResponseEntity.getBody();
    log.debug("Adding Jove Auth Token to token map {}", Objects.requireNonNull(token).getToken());
    tokens.put(userConnection, token);
    return token;
  }

  private ResponseEntity<JoveToken> retrieveNewTokenViaJoveApi(String joveAPIKey) {
    log.debug("Retrieving New Jove Access Token");
    try {
      return restTemplate.postForEntity(
          propertyHolder.getJoveApiUrl() + "/auth.php",
          getJoveAuthHttpEntity(joveAPIKey),
          JoveToken.class);
    } catch (RestClientException e) {
      log.error("Error Retrieving Jove Access Token {}", e.getMessage());
      throw new JoveTokenException("Error refreshing Jove Token", e);
    }
  }

  private HttpEntity<MultiValueMap<String, String>> getJoveAuthHttpEntity(String joveAPIKey) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
    map.add("key", joveAPIKey);

    return new HttpEntity<>(map, headers);
  }
}
