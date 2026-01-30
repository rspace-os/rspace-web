package com.researchspace.webapp.integrations.dsw;

import static com.researchspace.service.IntegrationsHandler.DSW_APP_NAME;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.model.User;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.UserManager;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
public class DSWClient {

  public static final String DSW_CONFIGURED_SERVERS = "DSW_CONFIGURED_SERVERS";
  public static final String DSW_URL = "DSW_URL";
  public static final String DSW_APIKEY = "DSW_APIKEY";
  public static final String DSW_ALIAS = "DSW_ALIAS";

  private final RestTemplate restTemplate;
  private @Autowired UserConnectionManager source;
  private @Autowired UserManager userManager;

  public DSWClient() {
    this.restTemplate = new RestTemplate();
  }

  private HttpHeaders getHttpHeaders(String serverAlias, DSWConnectionConfig connCfg) {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    String apiKey = getApiKey(serverAlias);
    headers.add("Authorization", "Bearer " + apiKey);
    return headers;
  }

  private String getApiKey(String serverAlias) {
    User subject = userManager.getAuthenticatedUserInSession();
    String accessToken =
        source
            .findByUserNameProviderName(subject.getUsername(), DSW_APP_NAME, serverAlias)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No UserConnection exists for app \""
                            + DSW_APP_NAME
                            + "\" and server alias /"
                            + serverAlias
                            + "\""))
            .getAccessToken();
    return accessToken;
  }

  public JsonNode currentUser(String serverAlias, AppConfigElementSet cfg)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    DSWConnectionConfig connCfg = new DSWConnectionConfig(cfg);
    return restTemplate
        .exchange(
            UriComponentsBuilder.fromUriString(connCfg.getRepositoryURL().get() + "/users/current")
                .build()
                .toUri(),
            HttpMethod.GET,
            new HttpEntity<>(getHttpHeaders(serverAlias, connCfg)),
            JsonNode.class)
        .getBody();
  }
}
