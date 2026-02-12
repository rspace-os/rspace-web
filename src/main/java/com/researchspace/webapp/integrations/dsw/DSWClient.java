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
import com.researchspace.webapp.integrations.dsw.model.DSWDocument;
import com.researchspace.webapp.integrations.dsw.model.DSWDocumentDTO;
import com.researchspace.webapp.integrations.dsw.model.DSWDocumentReference;
import com.researchspace.webapp.integrations.dsw.model.DSWDocuments;
import com.researchspace.webapp.integrations.dsw.model.DSWProjects;
import com.researchspace.webapp.integrations.dsw.model.DSWUser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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

  private HttpHeaders getHttpHeaders(String serverAlias, DSWConnectionConfig connCfg) {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    String apiKey = getApiKey(serverAlias);
    headers.add("Authorization", "Bearer " + apiKey);
    return headers;
  }

  private HttpHeaders getHttpHeadersFiles(String serverAlias, DSWConnectionConfig connCfg) {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(MediaType.TEXT_HTML));
    //    String apiKey = getApiKey(serverAlias);
    //    headers.add("Authorization", "Bearer " + apiKey);
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

  // public JsonNode getProjectsForCurrentUser(String serverAlias, AppConfigElementSet cfg)
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
        //    JsonNode projects =
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
            //                JsonNode.class)
            .getBody();
    return projects;
  }

  public JsonNode getDocumentsForProject(
      String serverAlias, AppConfigElementSet cfg, String projectUuid)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    DSWConnectionConfig connCfg = new DSWConnectionConfig(cfg);

    JsonNode projectDocuments =
        restTemplate
            .exchange(
                UriComponentsBuilder.fromUriString(
                        connCfg.getRepositoryURL().get()
                            + DSW_PATH_API
                            + "projects/"
                            + projectUuid
                            + "/documents")
                    .build()
                    .toUri(),
                HttpMethod.GET,
                new HttpEntity<>(getHttpHeaders(serverAlias, connCfg)),
                JsonNode.class)
            .getBody();

    return projectDocuments;
  }

  public JsonNode getDocsForCurrentUser(String serverAlias, AppConfigElementSet cfg)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    DSWConnectionConfig connCfg = new DSWConnectionConfig(cfg);

    //    JsonNode projects = getProjectsForCurrentUser(serverAlias, cfg);
    //
    //    DSWProjects dswProjects = null;
    //    try {
    //      dswProjects = mapper.readValue(projects.toString(), DSWProjects.class);
    //      System.out.println("@@@ This many projects: " + dswProjects.getProjects().length);
    //    } catch (Exception e) {
    //      System.out.println("@@@ Error! " + e.getMessage());
    //    }
    DSWProjects dswProjects = getProjectsForCurrentUser(serverAlias, cfg);

    List<String> projectUuids =
        Arrays.stream(dswProjects.getProjects()).map(p -> p.getUuid()).collect(Collectors.toList());

    List<DSWDocumentDTO> userDocuments = new ArrayList<>();

    for (String projectUuid : projectUuids) {
      JsonNode documents = getDocumentsForProject(serverAlias, cfg, projectUuid);
      // System.out.println("@@@ Documents for project: " + documents);
      try {
        DSWDocuments dswDocuments = mapper.readValue(documents.toString(), DSWDocuments.class);
        System.out.println("This many documents: " + dswDocuments.getDocuments().length);
        for (DSWDocument dswDocument : dswDocuments.getDocuments()) {
          DSWDocumentDTO dswDocumentDTO = new DSWDocumentDTO(dswDocument);
          userDocuments.add(dswDocumentDTO);
        }
      } catch (Exception e) {
        System.out.println("@@@ Documents error: " + e);
      }
    }

    System.out.println("@@@ This many aggregated documents: " + userDocuments.size());
    JsonNode userDocJson = null;

    try {
      String docsAsString = mapper.writeValueAsString(userDocuments);
      System.out.println("@@@ Docs as string: " + docsAsString);
      userDocJson = mapper.readTree(docsAsString);
    } catch (Exception e) {
      System.out.println("@@@ Error converting: " + e);
    }

    return userDocJson;
  }

  public JsonNode getDocumentURL(String serverAlias, AppConfigElementSet cfg, String documentUuid)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    DSWConnectionConfig connCfg = new DSWConnectionConfig(cfg);

    // JsonNode projectDocuments =
    String projectDocuments =
        restTemplate
            .exchange(
                UriComponentsBuilder.fromUriString(
                        connCfg.getRepositoryURL().get()
                            + DSW_PATH_API
                            + "documents/"
                            + documentUuid
                            + "/download")
                    .build()
                    .toUri(),
                HttpMethod.GET,
                new HttpEntity<>(getHttpHeaders(serverAlias, connCfg)),
                // JsonNode.class)
                String.class)
            .getBody();

    System.out.println("@@@ Document URL string: " + projectDocuments);
    try {
      return mapper.readValue(projectDocuments, JsonNode.class);
    } catch (Exception e) {
      System.out.println("@@@ Error getting URL: " + e);
      return null;
    }
  }

  public JsonNode importDswFile(String serverAlias, AppConfigElementSet cfg, String documentUuid)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    DSWConnectionConfig connCfg = new DSWConnectionConfig(cfg);

    System.out.println("@@@ Downloading file with uuid: " + documentUuid);
    JsonNode docUrl = getDocumentURL(serverAlias, cfg, documentUuid);
    String url = docUrl.get("url").toString();
    System.out.println("@@@ url for file: " + url);
    byte[] fileContents = null;

    try {
      DSWDocumentReference dswDocumentRef =
          mapper.readValue(docUrl.toString(), DSWDocumentReference.class);
      url = dswDocumentRef.getUrl();
      // Note that when retrieving the file we need to set that the URI has already
      // been encoded (since it has been on return from DSW), otherwise restTemplate
      // will encode it a second time.
      fileContents =
          restTemplate
              .exchange(
                  UriComponentsBuilder.fromUriString(url).build(true).toUri(),
                  HttpMethod.GET,
                  new HttpEntity<>(getHttpHeadersFiles(serverAlias, connCfg)),
                  byte[].class)
              .getBody();
    } catch (Exception e) {
      System.out.println("@@@ Something went wrong getting the file: " + e);
    }

    System.out.println("@@@ So here is the file I guess...: " + fileContents);

    return null;
  }

  public JsonNode getProjectsForCurrentUserJson(String serverAlias, AppConfigElementSet cfg)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
    DSWProjects projects = getProjectsForCurrentUser(serverAlias, cfg);
    return mapper.valueToTree(projects.getProjects());
  }

  public JsonNode importPlan(String serverAlias, AppConfigElementSet cfg, String planUuid)
      throws HttpClientErrorException, URISyntaxException, MalformedURLException {
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

    String projectName = plan.get("name").toString();

    try {
      // DSWProjectFromUUID projectFromUUID = mapper.readValue(json, DSWProjectFromUUID.class);
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
      }
      dmpManager.save(dmpUser.get());

    } catch (Exception e) {
      log.warn("Error attempting to save project with UUID " + planUuid, e);
    }

    return plan;
  }
}
