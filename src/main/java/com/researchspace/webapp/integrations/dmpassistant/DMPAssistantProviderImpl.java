package com.researchspace.webapp.integrations.dmpassistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.UserConnectionManager;
import java.net.URI;
import java.util.Collections;
import javax.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
public class DMPAssistantProviderImpl implements DMPAssistantProvider {

  @Value("${dmpassistant.base.url}")
  private String baseUrl;

  @Autowired private UserConnectionManager userConnectionManager;

  @Setter(value = AccessLevel.PROTECTED) // test purposes
  private RestTemplate restTemplate = new RestTemplate();

  private String urlMe;
  private String urlPlans;
  private String urlTemplates;

  @PostConstruct
  public void init() {
    this.urlMe = this.baseUrl + "/api/v2/me";
    this.urlPlans = this.baseUrl + "/api/v2/plans";
    this.urlTemplates = this.baseUrl + "/api/v2/templates";
  }

  @Override
  public JsonNode me(User user) {
    return getJson(URI.create(urlMe), user);
  }

  @Override
  public JsonNode listPlans(String page, String perPage, Boolean complete, User user) {
    UriComponentsBuilder uri =
        UriComponentsBuilder.fromUriString(urlPlans)
            .queryParam("page", page)
            .queryParam("per_page", perPage);
    if (complete != null) {
      uri.queryParam("complete", complete);
    }
    return getJson(uri.build().toUri(), user);
  }

  @Override
  public JsonNode getPlanById(String id, Boolean complete, User user) {
    UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(urlPlans + "/" + id);
    if (complete != null) {
      uri.queryParam("complete", complete);
    }
    return getJson(uri.build().toUri(), user);
  }

  @Override
  public JsonNode createPlan(JsonNode plan, User user) {
    HttpHeaders headers = bearerJsonHeaders(user);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return restTemplate
        .exchange(
            URI.create(urlPlans), HttpMethod.POST, new HttpEntity<>(plan, headers), JsonNode.class)
        .getBody();
  }

  @Override
  public JsonNode editPlanAnswers(String id, JsonNode answers, User user) {
    HttpHeaders headers = bearerJsonHeaders(user);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return restTemplate
        .exchange(
            URI.create(urlPlans + "/" + id),
            HttpMethod.PUT,
            new HttpEntity<>(answers, headers),
            JsonNode.class)
        .getBody();
  }

  @Override
  public JsonNode listTemplates(User user) {
    return getJson(URI.create(urlTemplates), user);
  }

  @Override
  public JsonNode getTemplateById(String id, User user) {
    return getJson(URI.create(urlTemplates + "/" + id), user);
  }

  private JsonNode getJson(URI uri, User user) {
    return restTemplate
        .exchange(uri, HttpMethod.GET, new HttpEntity<>(bearerJsonHeaders(user)), JsonNode.class)
        .getBody();
  }

  private HttpHeaders bearerJsonHeaders(User user) {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.setBearerAuth(getTokenFor(user));
    return headers;
  }

  private String getTokenFor(User user) {
    return userConnectionManager
        .findByUserNameProviderName(user.getUsername(), IntegrationsHandler.DMPASSISTANT_APP_NAME)
        .map(UserConnection::getAccessToken)
        .filter(StringUtils::isNotBlank)
        .orElseThrow(
            () ->
                new HttpClientErrorException(
                    HttpStatus.NOT_FOUND, "DMP Assistant user token not found"));
  }
}
