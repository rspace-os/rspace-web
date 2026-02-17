package com.researchspace.webapp.integrations.dsw;

import static com.researchspace.service.IntegrationsHandler.DSW_APP_NAME;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dmps.DmpDto;
import com.researchspace.service.DMPManager;
import com.researchspace.service.MediaManager;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.integrations.dsw.exception.DSWProjectRetrievalException;
import com.researchspace.webapp.integrations.dsw.model.DSWProjects;
import com.researchspace.webapp.integrations.dsw.model.DSWUser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Optional;
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

  public static final String DSW_PATH_API = "/wizard-api/";
  public static final String DSW_PATH_PROJECTS = "/wizard/projects/";

  @Setter
  @Getter
  @Value("${dsw.server.config:}")
  private String configurationMap;

  private final RestTemplate restTemplate;
  @Autowired private UserConnectionManager source;
  @Autowired private UserManager userManager;
  @Autowired private MediaManager mediaManager;
  @Autowired private DMPManager dmpManager;

  private ObjectMapper mapper = new ObjectMapper();

  public DSWClient() {
    this.restTemplate = new RestTemplate();
  }

  @Autowired
  public DSWClient(
      UserConnectionManager userConnectionManager,
      UserManager userManager,
      MediaManager mediaManager,
      DMPManager dmpManager) {
    this.source = userConnectionManager;
    this.userManager = userManager;
    this.mediaManager = mediaManager;
    this.dmpManager = dmpManager;
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
    User subject = userManager.get(-12l);
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
            UriComponentsBuilder.fromUriString(
                    connCfg.getRepositoryURL().get() + DSW_PATH_API + "users/current")
                .build()
                .toUri(),
            HttpMethod.GET,
            new HttpEntity<>(getHttpHeaders(serverAlias, connCfg)),
            JsonNode.class)
        .getBody();
  }

  public DSWProjects getProjectsForCurrentUser(String serverAlias, AppConfigElementSet cfg)
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

    DSWProjects projects =
        restTemplate
            .exchange(
                UriComponentsBuilder.fromUriString(
                        connCfg.getRepositoryURL().get()
                            + DSW_PATH_API
                            + "projects?userUuids="
                            + dswUser.getUuid())
                    .build()
                    .toUri(),
                HttpMethod.GET,
                new HttpEntity<>(getHttpHeaders(serverAlias, connCfg)),
                DSWProjects.class)
            .getBody();
    return projects;
  }

  public JsonNode getProjectsForCurrentUserJson(String serverAlias, AppConfigElementSet cfg)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    DSWProjects projects = getProjectsForCurrentUser(serverAlias, cfg);
    return mapper.valueToTree(projects.getProjects());
  }

  public JsonNode importPlan(String serverAlias, AppConfigElementSet cfg, String planUuid)
      throws HttpClientErrorException,
          URISyntaxException,
          MalformedURLException,
          DSWProjectRetrievalException {

    DSWConnectionConfig connCfg = new DSWConnectionConfig(cfg);

    System.out.println("@@@ Downloading plan with uuid: " + planUuid);
    JsonNode plan =
        restTemplate
            .exchange(
                UriComponentsBuilder.fromUriString(
                        connCfg.getRepositoryURL().get()
                            + DSW_PATH_API
                            + "projects/"
                            + planUuid
                            + "/questionnaire")
                    .build()
                    .toUri(),
                HttpMethod.GET,
                new HttpEntity<>(getHttpHeaders(serverAlias, connCfg)),
                JsonNode.class)
            .getBody();

    String json = plan.toString();
    InputStream is = new ByteArrayInputStream(json.getBytes());

    String projectName = plan.get("name").textValue();

    try {
      EcatDocumentFile file =
          mediaManager.saveNewDMP(projectName, is, cfg.getUserAppConfig().getUser(), null);

      Optional<DMPUser> dmpUser =
          dmpManager.findByDmpId(planUuid, cfg.getUserAppConfig().getUser());
      if (dmpUser.isEmpty()) {
        dmpUser =
            Optional.of(
                new DMPUser(
                    cfg.getUserAppConfig().getUser(),
                    new DmpDto(
                        planUuid,
                        projectName,
                        DMPSource.DSW,
                        null,
                        connCfg.getRepositoryURL().get() + DSW_PATH_PROJECTS + planUuid)));
      }
      if (file != null) {
        dmpUser.get().setDmpDownloadFile(file);
      } else {
        log.warn("Unexpected null DSW project");
        throw new DSWProjectRetrievalException("DSW project " + planUuid + " was empty");
      }
      dmpManager.save(dmpUser.get());
    } catch (Exception e) {
      log.warn("Error attempting to save project with UUID " + planUuid, e);
      throw new DSWProjectRetrievalException(
          "Error attempting to save project with UUID " + planUuid, e);
    }
    return plan;
  }
}
