package com.researchspace.webapp.integrations.pyrat;

import static com.researchspace.service.IntegrationsHandler.PYRAT_APP_NAME;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.model.User;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.integrations.MultiInstanceBaseClient;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import lombok.Getter;
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
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
public class PyratClient extends MultiInstanceBaseClient<PyratServerConfigurationDTO> {

  public static final String PYRAT_CONFIGURED_SERVERS = "PYRAT_CONFIGURED_SERVERS";
  public static final String PYRAT_URL = "PYRAT_URL";
  public static final String PYRAT_APIKEY = "PYRAT_APIKEY";
  public static final String PYRAT_ALIAS = "PYRAT_ALIAS";

  class Listing {

    protected JsonNode payload;
    protected String totalCount;

    public Listing(JsonNode payload, String totalCount) {
      this.payload = payload;
      this.totalCount = totalCount;
    }
  }

  @Setter
  @Getter
  @Value("${pyrat.server.config:}")
  private String configurationMap;

  private final RestTemplate restTemplate;
  private @Autowired UserConnectionManager source;
  private @Autowired UserManager userManager;

  public PyratClient() {
    this.restTemplate = new RestTemplate();
  }

  @Override
  protected TypeReference<Map<String, PyratServerConfigurationDTO>> getTypeReference() {
    return new TypeReference<>() {};
  }

  public JsonNode version(String serverAlias)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    return restTemplate
        .exchange(
            UriComponentsBuilder.fromUriString(getApiBaseUrl(serverAlias) + "/version")
                .build()
                .toUri(),
            HttpMethod.GET,
            new HttpEntity<>(getHttpHeaders(serverAlias)),
            JsonNode.class)
        .getBody();
  }

  public JsonNode locations(String serverAlias, MultiValueMap<String, String> queryParams)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    return restTemplate
        .exchange(
            UriComponentsBuilder.fromUriString(getApiBaseUrl(serverAlias) + "/locations")
                .queryParams(queryParams)
                .build()
                .toUri(),
            HttpMethod.GET,
            new HttpEntity<>(getHttpHeaders(serverAlias)),
            JsonNode.class)
        .getBody();
  }

  public Listing animals(String serverAlias, MultiValueMap<String, String> queryParams)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    var response =
        restTemplate.exchange(
            UriComponentsBuilder.fromUriString(getApiBaseUrl(serverAlias) + "/animals")
                .queryParams(queryParams)
                .build()
                .toUri(),
            HttpMethod.GET,
            new HttpEntity<>(getHttpHeaders(serverAlias)),
            JsonNode.class);
    return new Listing(response.getBody(), response.getHeaders().get("X-Total-Count").get(0));
  }

  public Listing pups(String serverAlias, MultiValueMap<String, String> queryParams)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    var response =
        restTemplate.exchange(
            UriComponentsBuilder.fromUriString(getApiBaseUrl(serverAlias) + "/pups")
                .queryParams(queryParams)
                .build()
                .toUri(),
            HttpMethod.GET,
            new HttpEntity<>(getHttpHeaders(serverAlias)),
            JsonNode.class);
    return new Listing(response.getBody(), response.getHeaders().get("X-Total-Count").get(0));
  }

  private HttpHeaders getHttpHeaders(String serverAlias) {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.add(
        "Authorization",
        "Basic "
            + Base64.getEncoder()
                .encodeToString(
                    new String(getServerSecret(serverAlias) + ":" + getApiKey(serverAlias))
                        .getBytes()));
    return headers;
  }

  private String getApiKey(String serverAlias) {
    User subject = userManager.getAuthenticatedUserInSession();
    return source
        .findByUserNameProviderName(subject.getUsername(), PYRAT_APP_NAME, serverAlias)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "No UserConnection exists for app \""
                        + PYRAT_APP_NAME
                        + "\" and server alias /"
                        + serverAlias
                        + "\""))
        .getAccessToken();
  }

  public JsonNode projects(String serverAlias, MultiValueMap<String, String> queryParams)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    return restTemplate
        .exchange(
            UriComponentsBuilder.fromUriString(getApiBaseUrl(serverAlias) + "/projects")
                .queryParams(queryParams)
                .build()
                .toUri(),
            HttpMethod.GET,
            new HttpEntity<>(getHttpHeaders(serverAlias)),
            JsonNode.class)
        .getBody();
  }

  public JsonNode users(String serverAlias, MultiValueMap<String, String> queryParams)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    return restTemplate
        .exchange(
            UriComponentsBuilder.fromUriString(getApiBaseUrl(serverAlias) + "/users")
                .queryParams(queryParams)
                .build()
                .toUri(),
            HttpMethod.GET,
            new HttpEntity<>(getHttpHeaders(serverAlias)),
            JsonNode.class)
        .getBody();
  }

  public JsonNode licenses(String serverAlias, MultiValueMap<String, String> queryParams)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    return restTemplate
        .exchange(
            UriComponentsBuilder.fromUriString(getApiBaseUrl(serverAlias) + "/licenses")
                .queryParams(queryParams)
                .build()
                .toUri(),
            HttpMethod.GET,
            new HttpEntity<>(getHttpHeaders(serverAlias)),
            JsonNode.class)
        .getBody();
  }

  private String getServerSecret(String serverAlias) {
    if (!this.getServerMapByAlias().containsKey(serverAlias)
        || StringUtils.isBlank(this.getServerMapByAlias().get(serverAlias).getToken())) {
      throw new HttpClientErrorException(
          HttpStatus.NOT_FOUND, "Pyrat server token for alias=\"" + serverAlias + "\" not found");
    }
    return this.getServerMapByAlias().get(serverAlias).getToken();
  }
}
