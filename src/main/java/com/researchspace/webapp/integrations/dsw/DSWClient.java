package com.researchspace.webapp.integrations.dsw;

import static com.researchspace.service.IntegrationsHandler.DSW_APP_NAME;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.User;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.integrations.dsw.model.DSWProjects;
import com.researchspace.webapp.integrations.dsw.model.DSWUser;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

  @Setter
  @Getter
  @Value("${dsw.server.config:}")
  private String configurationMap;

  private final RestTemplate restTemplate;
  private @Autowired UserConnectionManager source;
  private @Autowired UserManager userManager;
  private ObjectMapper mapper = new ObjectMapper();

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
    // User subject = userManager.getAuthenticatedUserInSession();
    User subject = userManager.get(3l);
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

  public JsonNode getProjectsForCurrentUser(String serverAlias, AppConfigElementSet cfg)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    DSWConnectionConfig connCfg = new DSWConnectionConfig(cfg);

    JsonNode currentUser = currentUser(serverAlias, cfg);
    DSWUser dswUser = null;
    try {
      dswUser = mapper.readValue(currentUser.toString(), DSWUser.class);
      System.out.println("@@@ DSW User: " + dswUser.getEmail());
    } catch (Exception e) {
      System.out.println("@@@ Error! " + e.getMessage());
    }

    JsonNode projects =
        restTemplate
            .exchange(
                UriComponentsBuilder.fromUriString(
                        connCfg.getRepositoryURL().get()
                            + "/projects?userUuids="
                            + dswUser.getUuid())
                    .build()
                    .toUri(),
                HttpMethod.GET,
                new HttpEntity<>(getHttpHeaders(serverAlias, connCfg)),
                JsonNode.class)
            .getBody();

    return projects;
  }

  public JsonNode getDocsForCurrentUser(String serverAlias, AppConfigElementSet cfg)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    DSWConnectionConfig connCfg = new DSWConnectionConfig(cfg);

    JsonNode projects = getProjectsForCurrentUser(serverAlias, cfg);

    DSWProjects dswProjects = null;
    try {
      dswProjects = mapper.readValue(projects.toString(), DSWProjects.class);
      System.out.println("@@@ This many projects: " + dswProjects.getProjects().length);
    } catch (Exception e) {
      System.out.println("@@@ Error! " + e.getMessage());
    }

    List<String> projectUuids = Arrays.stream(dswProjects.getProjects())
        .map(p -> p.getUuid())
        .collect(Collectors.toList());

    JsonNode documents = null;
    //    documents =
    //        restTemplate
    //            .exchange(
    //                UriComponentsBuilder.fromUriString(
    //                        connCfg.getRepositoryURL().get()
    //                            + "/projects?userUuids=")
    //                    .build()
    //                    .toUri(),
    //                HttpMethod.GET,
    //                new HttpEntity<>(getHttpHeaders(serverAlias, connCfg)),
    //                JsonNode.class)
    //            .getBody();

    return documents;
  }
}
