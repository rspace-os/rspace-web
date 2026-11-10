package com.researchspace.webapp.integrations.dsw;

import static com.researchspace.service.IntegrationsHandler.DSW_APP_NAME;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.researchspace.core.util.version.SemanticVersion;
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
import com.researchspace.webapp.integrations.dsw.model.DSWProject;
import com.researchspace.webapp.integrations.dsw.model.DSWProjects;
import com.researchspace.webapp.integrations.dsw.model.DSWUser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.UnknownContentTypeException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
public class DSWClient {

  public static final String DSW_URL = "DSW_URL";
  public static final String DSW_APIKEY = "DSW_APIKEY";
  public static final String DSW_ALIAS = "DSW_ALIAS";

  public static final String DSW_PATH_API_LEGACY = "/wizard-api/";
  public static final String DSW_PATH_API_CURRENT = "/api/";
  public static final String DSW_PATH_PROJECTS_LEGACY = "/wizard/projects/";
  public static final String DSW_PATH_PROJECTS_CURRENT = "/projects/";

  /** First DSW release that uses the current API and project page paths. */
  public static final SemanticVersion DSW_API_PATH_CHANGE_VERSION = new SemanticVersion("5.0.0");

  /** How long a server's detected paths are reused before its version is checked again. */
  public static final int DSW_API_PATH_CACHE_TTL_MINUTES = 15;

  private static final int DSW_API_PATH_CACHE_MAX_SIZE = 100;

  // Two or three parts, so a future "5.1" is still recognised. SemanticVersion treats a missing
  // patch number as matching any, so "5.0" counts as 5.0.0.
  private static final Pattern DSW_RELEASE_VERSION = Pattern.compile("\\d+\\.\\d+(?:\\.\\d+)?");

  public static final int DSW_PROJECT_NAME_LENGTH_MAX = 255;
  public static final int DSW_PROJECT_DESCRIPTION_LENGTH_MAX = 250;
  public static final String TRUNCATION_SUFFIX = "...";

  /** The paths a DSW server uses, which depend on its release version. */
  private enum DSWPaths {
    LEGACY(DSW_PATH_API_LEGACY, DSW_PATH_PROJECTS_LEGACY),
    CURRENT(DSW_PATH_API_CURRENT, DSW_PATH_PROJECTS_CURRENT);

    private final String api;
    private final String projects;

    DSWPaths(String api, String projects) {
      this.api = api;
      this.projects = projects;
    }
  }

  private final RestTemplate restTemplate;
  // Server URL, without trailing slash, to the paths it uses.
  private final Cache<String, DSWPaths> pathsCache;
  @Autowired private UserConnectionManager source;
  @Autowired private UserManager userManager;
  @Autowired private MediaManager mediaManager;
  @Autowired private DMPManager dmpManager;

  private ObjectMapper mapper = new ObjectMapper();

  public DSWClient() {
    this(new RestTemplate(), Ticker.systemTicker());
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
    this.pathsCache = buildPathsCache(Ticker.systemTicker());
  }

  DSWClient(RestTemplate restTemplate, Ticker ticker) {
    this.restTemplate = restTemplate;
    this.pathsCache = buildPathsCache(ticker);
  }

  private static Cache<String, DSWPaths> buildPathsCache(Ticker ticker) {
    return CacheBuilder.newBuilder()
        .maximumSize(DSW_API_PATH_CACHE_MAX_SIZE)
        .expireAfterWrite(DSW_API_PATH_CACHE_TTL_MINUTES, TimeUnit.MINUTES)
        .ticker(ticker)
        .build();
  }

  /**
   * DSW 5.0.0 moved its API from {@link #DSW_PATH_API_LEGACY} to {@link #DSW_PATH_API_CURRENT}, and
   * its project pages from {@link #DSW_PATH_PROJECTS_LEGACY} to {@link #DSW_PATH_PROJECTS_CURRENT}.
   * The release version is read from the unauthenticated info endpoint at the new path. Only a
   * clear "not a 5.x server" answer selects the legacy paths: a 404, a redirect, a body that is not
   * JSON, or JSON reporting no version or one older than 5.0.0.
   *
   * <p>The detected paths are cached per server URL for {@link #DSW_API_PATH_CACHE_TTL_MINUTES}
   * minutes. Failures are not cached, so the next call checks again.
   *
   * @throws RestClientException for any other failure, such as a timeout, connection error or other
   *     HTTP error status, so that a temporary fault on a 5.x server is not mistaken for a
   *     pre-5.0.0 server
   */
  String getApiBaseUrl(String savedUrl) {
    String serverUrl = withoutTrailingSlash(savedUrl);
    return serverUrl + getPaths(serverUrl).api;
  }

  /**
   * Base URL of the DSW project pages, which a project UUID is appended to. Uses the same version
   * detection and cache as {@link #getApiBaseUrl(String)}.
   */
  String getProjectsBaseUrl(String savedUrl) {
    String serverUrl = withoutTrailingSlash(savedUrl);
    return serverUrl + getPaths(serverUrl).projects;
  }

  private DSWPaths getPaths(String serverUrl) {
    try {
      // Concurrent misses for the same URL share one version check.
      return pathsCache.get(serverUrl, () -> detectPaths(serverUrl));
    } catch (UncheckedExecutionException | ExecutionException e) {
      if (e.getCause() instanceof RestClientException restClientException) {
        throw restClientException;
      }
      throw new IllegalStateException(
          "Could not detect the DSW paths for " + serverUrl, e.getCause());
    }
  }

  private DSWPaths detectPaths(String serverUrl) {
    Optional<SemanticVersion> version = getServerVersion(serverUrl);
    DSWPaths paths =
        version.filter(v -> v.isSameOrNewerThan(DSW_API_PATH_CHANGE_VERSION)).isPresent()
            ? DSWPaths.CURRENT
            : DSWPaths.LEGACY;
    // Once per server per cache period; getServerVersion logs the reason for any fallback.
    log.info(
        "DSW at {} reports version {}: using API path {} and project path {}",
        serverUrl,
        version.map(SemanticVersion::toString).orElse("unknown"),
        paths.api,
        paths.projects);
    return paths;
  }

  private String getApiBaseUrl(DSWConnectionConfig connCfg) throws MalformedURLException {
    return getApiBaseUrl(connCfg.getRepositoryURL().get().toString());
  }

  /** The DSW paths start with a slash, so any trailing slash on the saved URL is dropped. */
  static String withoutTrailingSlash(String url) {
    return url.replaceAll("/+$", "");
  }

  private void logNonJsonVersionInfo(String serverUrl) {
    log.debug(
        "DSW version info at {} is not JSON, assuming older than {}",
        serverUrl,
        DSW_API_PATH_CHANGE_VERSION);
  }

  private Optional<SemanticVersion> getServerVersion(String serverUrl) {
    JsonNode info;
    try {
      info =
          restTemplate.getForObject(
              UriComponentsBuilder.fromUriString(serverUrl + DSW_PATH_API_CURRENT).build().toUri(),
              JsonNode.class);
    } catch (HttpClientErrorException.NotFound e) {
      // Log only the status: the exception message can include part of the response body.
      log.debug(
          "No DSW version info at {} (HTTP 404), assuming older than {}",
          serverUrl,
          DSW_API_PATH_CHANGE_VERSION);
      return Optional.empty();
    } catch (UnknownContentTypeException e) {
      logNonJsonVersionInfo(serverUrl);
      return Optional.empty();
    } catch (RestClientException e) {
      if (e.getCause() instanceof HttpMessageNotReadableException) {
        logNonJsonVersionInfo(serverUrl);
        return Optional.empty();
      }
      throw e;
    }
    if (info == null) {
      log.debug(
          "Empty DSW version info at {}, assuming older than {}",
          serverUrl,
          DSW_API_PATH_CHANGE_VERSION);
      return Optional.empty();
    }
    // releaseVersion is "5.0.0"; version is "v5.0.0~f3c60c086"
    String reported = info.path("releaseVersion").asText("");
    if (reported.isBlank()) {
      reported = info.path("version").asText("");
    }
    Matcher matcher = DSW_RELEASE_VERSION.matcher(reported);
    if (!matcher.find()) {
      log.warn(
          "DSW at {} returned version info without a recognisable version ({}), assuming older"
              + " than {}",
          serverUrl,
          forLog(reported),
          DSW_API_PATH_CHANGE_VERSION);
      return Optional.empty();
    }
    SemanticVersion version = new SemanticVersion(matcher.group());
    return Optional.of(version);
  }

  // The value comes from a remote server: keep it short and on one line.
  private static String forLog(String reported) {
    String oneLine = reported.replaceAll("\\p{Cntrl}", " ");
    return oneLine.isEmpty() ? "none" : StringUtils.abbreviate(oneLine, 50);
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
    return currentUser(serverAlias, connCfg, getApiBaseUrl(connCfg));
  }

  private JsonNode currentUser(String serverAlias, DSWConnectionConfig connCfg, String apiBaseUrl) {
    return restTemplate
        .exchange(
            UriComponentsBuilder.fromUriString(apiBaseUrl + "users/current").build().toUri(),
            HttpMethod.GET,
            new HttpEntity<>(getHttpHeaders(serverAlias, connCfg)),
            JsonNode.class)
        .getBody();
  }

  public DSWProjects getProjectsForCurrentUser(String serverAlias, AppConfigElementSet cfg)
      throws HttpClientErrorException,
          URISyntaxException,
          MalformedURLException,
          DSWProjectRetrievalException {
    DSWConnectionConfig connCfg = new DSWConnectionConfig(cfg);
    return getProjectsForCurrentUser(serverAlias, connCfg, getApiBaseUrl(connCfg));
  }

  private DSWProjects getProjectsForCurrentUser(
      String serverAlias, DSWConnectionConfig connCfg, String apiBaseUrl)
      throws DSWProjectRetrievalException {
    JsonNode currentUser = currentUser(serverAlias, connCfg, apiBaseUrl);
    DSWUser dswUser = null;
    try {
      dswUser = mapper.readValue(currentUser.toString(), DSWUser.class);
    } catch (Exception e) {
      log.warn("Error reading current user details", e);
      throw new DSWProjectRetrievalException("Error retrieving DSW user details");
    }

    DSWProjects projects =
        restTemplate
            .exchange(
                UriComponentsBuilder.fromUriString(
                        apiBaseUrl + "projects?userUuids=" + dswUser.getUuid())
                    .build()
                    .toUri(),
                HttpMethod.GET,
                new HttpEntity<>(getHttpHeaders(serverAlias, connCfg)),
                DSWProjects.class)
            .getBody();
    return projects;
  }

  public JsonNode getProjectsForCurrentUserJson(String serverAlias, AppConfigElementSet cfg)
      throws HttpClientErrorException,
          URISyntaxException,
          MalformedURLException,
          DSWProjectRetrievalException {
    DSWProjects projects = getProjectsForCurrentUser(serverAlias, cfg);
    return mapper.valueToTree(projects.getProjects());
  }

  public JsonNode importPlan(String serverAlias, AppConfigElementSet cfg, String planUuid)
      throws HttpClientErrorException,
          URISyntaxException,
          MalformedURLException,
          DSWProjectRetrievalException {

    // The description metadata does not come with the questionnaire, only
    // in the list of projects, so first of all we need to retrieve those.
    DSWConnectionConfig connCfg = new DSWConnectionConfig(cfg);
    String apiBaseUrl = getApiBaseUrl(connCfg);
    DSWProjects projects = getProjectsForCurrentUser(serverAlias, connCfg, apiBaseUrl);
    List<DSWProject> currentProjects =
        Arrays.stream(projects.getProjects())
            .filter(p -> p.getUuid().equals(planUuid))
            .collect(Collectors.toList());
    if (currentProjects.isEmpty()) {
      throw new DSWProjectRetrievalException("Cannot find project with UUID: " + planUuid);
    } else if (currentProjects.size() > 1) {
      throw new DSWProjectRetrievalException("Two many projects returned with UUID: " + planUuid);
    }

    JsonNode plan =
        restTemplate
            .exchange(
                UriComponentsBuilder.fromUriString(
                        apiBaseUrl + "projects/" + planUuid + "/questionnaire")
                    .build()
                    .toUri(),
                HttpMethod.GET,
                new HttpEntity<>(getHttpHeaders(serverAlias, connCfg)),
                JsonNode.class)
            .getBody();

    String json = plan.toString();
    InputStream is = new ByteArrayInputStream(json.getBytes());

    String projectName =
        truncateDswString(plan.get("name").textValue(), DSW_PROJECT_NAME_LENGTH_MAX);
    String projectDescription =
        truncateDswString(
            currentProjects.get(0).getDescription(), DSW_PROJECT_DESCRIPTION_LENGTH_MAX);

    try {
      EcatDocumentFile file =
          mediaManager.saveNewDMPWithDescription(
              projectName, is, cfg.getUserAppConfig().getUser(), null, projectDescription);

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
                        UriComponentsBuilder.fromUriString(
                                getProjectsBaseUrl(connCfg.getRepositoryURL().get().toString())
                                    + planUuid)
                            .build()
                            .toUri()
                            .toString())));
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

  // Truncates a string that is past a maximum length and adds a suffix
  // denoting that truncation has occurred to the end.
  private String truncateDswString(String toTruncate, int maxLength) {
    if (null == toTruncate || toTruncate.length() <= maxLength) {
      return toTruncate;
    } else {
      String truncatedString =
          toTruncate.substring(0, maxLength - TRUNCATION_SUFFIX.length()) + TRUNCATION_SUFFIX;
      return truncatedString;
    }
  }
}
