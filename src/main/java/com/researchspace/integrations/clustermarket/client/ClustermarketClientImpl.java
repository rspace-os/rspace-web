package com.researchspace.integrations.clustermarket.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.UserConnectionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Retryable(
    value = {RestClientException.class, HttpServerErrorException.class},
    maxAttemptsExpression = "${services.retry.max-attempts}",
    backoff = @Backoff(delayExpression = "${services.retry.back-off-delay-in-millis}"))
@Component
public class ClustermarketClientImpl implements ClustermarketClient {
  private RestTemplate restTemplate = new RestTemplate();
  private UserConnectionManager userConnectionManager;

  @Value("${clustermarket.api.url}")
  private String clustermarketApiUrl;

  public ClustermarketClientImpl(UserConnectionManager userConnectionManager) {
    this.userConnectionManager = userConnectionManager;
  }

  public JsonNode getBookings(User user, String accessToken) {
    log.debug("call to clustermarket service to get all bookings for user: " + user);
    HttpHeaders headers = new HttpHeaders();
    headers.add("Authorization", String.format("Bearer %s", accessToken));
    return restTemplate
        .exchange(
            clustermarketApiUrl + "bookings",
            HttpMethod.GET,
            new HttpEntity<>(null, headers),
            JsonNode.class)
        .getBody();
  }

  public JsonNode getBookingDetails(String id, User user) {
    log.debug("call to clustermarket service to get BookingDetails");
    String token = storedToken(user);
    HttpHeaders headers = new HttpHeaders();
    headers.add("Authorization", String.format("Bearer %s", token));
    return restTemplate
        .exchange(
            clustermarketApiUrl + "bookings/" + id,
            HttpMethod.GET,
            new HttpEntity<>(null, headers),
            JsonNode.class)
        .getBody();
  }

  public JsonNode getEquipmentDetails(String id, User user) {
    log.debug("call to clustermarket service to get EquipmentDetails");
    String token = storedToken(user);
    HttpHeaders headers = new HttpHeaders();
    headers.add("Authorization", String.format("Bearer %s", token));
    return restTemplate
        .exchange(
            clustermarketApiUrl + "equipment/" + id,
            HttpMethod.GET,
            new HttpEntity<>(null, headers),
            JsonNode.class)
        .getBody();
  }

  // RSDEV-1525: IntegrationInfo withholds the token, so read the stored one
  private String storedToken(User user) {
    return userConnectionManager
        .findByUserNameProviderName(user.getUsername(), IntegrationsHandler.CLUSTERMARKET_APP_NAME)
        .map(UserConnection::getAccessToken)
        .orElse(null);
  }
}
