package com.researchspace.webapp.integrations.pyrat;

import static com.researchspace.service.IntegrationsHandler.PYRAT_APP_NAME;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.model.User;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.UserManager;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
public class PyratClient {

  class Listing {
    JsonNode payload;
    String totalCount;

    public Listing(JsonNode payload, String totalCount) {
      this.payload = payload;
      this.totalCount = totalCount;
    }
  }

  @Value("${pyrat.url}")
  private String pyratApiUrl;

  @Value("${pyrat.client.token}")
  private String pyratClientToken;

  private final RestTemplate restTemplate;
  private @Autowired UserConnectionManager source;
  private @Autowired UserManager userManager;

  public PyratClient() {
    this.restTemplate = new RestTemplate();
  }

  public JsonNode version()
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    return restTemplate
        .exchange(
            UriComponentsBuilder.fromUriString(pyratApiUrl + "/version").build().toUri(),
            HttpMethod.GET,
            new HttpEntity<>(getHttpHeaders()),
            JsonNode.class)
        .getBody();
  }

  public JsonNode locations(MultiValueMap<String, String> queryParams)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    return restTemplate
        .exchange(
            UriComponentsBuilder.fromUriString(pyratApiUrl + "/locations")
                .queryParams(queryParams)
                .build()
                .toUri(),
            HttpMethod.GET,
            new HttpEntity<>(getHttpHeaders()),
            JsonNode.class)
        .getBody();
  }

  public Listing animals(MultiValueMap<String, String> queryParams)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    var response =
        restTemplate.exchange(
            UriComponentsBuilder.fromUriString(pyratApiUrl + "/animals")
                .queryParams(queryParams)
                .build()
                .toUri(),
            HttpMethod.GET,
            new HttpEntity<>(getHttpHeaders()),
            JsonNode.class);
    return new Listing(response.getBody(), response.getHeaders().get("X-Total-Count").get(0));
  }

  public Listing pups(MultiValueMap<String, String> queryParams)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    var response =
        restTemplate.exchange(
            UriComponentsBuilder.fromUriString(pyratApiUrl + "/pups")
                .queryParams(queryParams)
                .build()
                .toUri(),
            HttpMethod.GET,
            new HttpEntity<>(getHttpHeaders()),
            JsonNode.class);
    return new Listing(response.getBody(), response.getHeaders().get("X-Total-Count").get(0));
  }

  private HttpHeaders getHttpHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.add(
        "Authorization",
        "Basic "
            + Base64.getEncoder()
                .encodeToString(new String(pyratClientToken + ":" + getApiKey()).getBytes()));
    return headers;
  }

  private String getApiKey() {
    User subject = userManager.getAuthenticatedUserInSession();
    return source
        .findByUserNameProviderName(subject.getUsername(), PYRAT_APP_NAME)
        .orElseThrow(
            () -> new IllegalArgumentException("No UserConnection exists for: " + PYRAT_APP_NAME))
        .getAccessToken();
  }

  public JsonNode projects(MultiValueMap<String, String> queryParams)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    return restTemplate
        .exchange(
            UriComponentsBuilder.fromUriString(pyratApiUrl + "/projects")
                .queryParams(queryParams)
                .build()
                .toUri(),
            HttpMethod.GET,
            new HttpEntity<>(getHttpHeaders()),
            JsonNode.class)
        .getBody();
  }

  public JsonNode users(MultiValueMap<String, String> queryParams)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    return restTemplate
        .exchange(
            UriComponentsBuilder.fromUriString(pyratApiUrl + "/users")
                .queryParams(queryParams)
                .build()
                .toUri(),
            HttpMethod.GET,
            new HttpEntity<>(getHttpHeaders()),
            JsonNode.class)
        .getBody();
  }

  public JsonNode licenses(MultiValueMap<String, String> queryParams)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    return restTemplate
        .exchange(
            UriComponentsBuilder.fromUriString(pyratApiUrl + "/licenses")
                .queryParams(queryParams)
                .build()
                .toUri(),
            HttpMethod.GET,
            new HttpEntity<>(getHttpHeaders()),
            JsonNode.class)
        .getBody();
  }
}
