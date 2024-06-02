package com.researchspace.integrations.clustermarket.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.model.User;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.service.IntegrationsHandler;
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
  private IntegrationsHandler integrationsHandler;

  @Value("${clustermarket.api.url}")
  private String clustermarketApiUrl;

  public ClustermarketClientImpl(IntegrationsHandler integrationsHandler) {
    this.integrationsHandler = integrationsHandler;
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
    IntegrationInfo inInfo =
        integrationsHandler.getIntegration(user, IntegrationsHandler.CLUSTERMARKET_APP_NAME);
    String token = (String) inInfo.getOptions().get(IntegrationsHandler.ACCESS_TOKEN_SETTING);
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
    IntegrationInfo inInfo =
        integrationsHandler.getIntegration(user, IntegrationsHandler.CLUSTERMARKET_APP_NAME);
    String token = (String) inInfo.getOptions().get(IntegrationsHandler.ACCESS_TOKEN_SETTING);
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
}
