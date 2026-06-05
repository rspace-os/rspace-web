package com.researchspace.webapp.integrations.dmpassistant;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Collections;
import javax.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
public class DMPAssistantProviderImpl implements DMPAssistantProvider {

  @Value("${dmpassistant.base.url}")
  private String baseUrl;

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
  public JsonNode me(String accessToken) {
    return getJson(URI.create(urlMe), accessToken);
  }

  @Override
  public JsonNode listPlans(String page, String perPage, Boolean complete, String accessToken) {
    UriComponentsBuilder uri =
        UriComponentsBuilder.fromUriString(urlPlans)
            .queryParam("page", page)
            .queryParam("per_page", perPage);
    if (complete != null) {
      uri.queryParam("complete", complete);
    }
    return getJson(uri.build().toUri(), accessToken);
  }

  @Override
  public JsonNode getPlanById(String id, Boolean complete, String accessToken) {
    UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(urlPlans + "/" + id);
    if (complete != null) {
      uri.queryParam("complete", complete);
    }
    return getJson(uri.build().toUri(), accessToken);
  }

  @Override
  public JsonNode createPlan(JsonNode plan, String accessToken) {
    HttpHeaders headers = bearerJsonHeaders(accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return restTemplate
        .exchange(
            URI.create(urlPlans), HttpMethod.POST, new HttpEntity<>(plan, headers), JsonNode.class)
        .getBody();
  }

  @Override
  public JsonNode editPlanAnswers(String id, JsonNode answers, String accessToken) {
    HttpHeaders headers = bearerJsonHeaders(accessToken);
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
  public JsonNode listTemplates(String accessToken) {
    return getJson(URI.create(urlTemplates), accessToken);
  }

  @Override
  public JsonNode getTemplateById(String id, String accessToken) {
    return getJson(URI.create(urlTemplates + "/" + id), accessToken);
  }

  private JsonNode getJson(URI uri, String accessToken) {
    return restTemplate
        .exchange(
            uri, HttpMethod.GET, new HttpEntity<>(bearerJsonHeaders(accessToken)), JsonNode.class)
        .getBody();
  }

  private HttpHeaders bearerJsonHeaders(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.setBearerAuth(accessToken);
    return headers;
  }
}
