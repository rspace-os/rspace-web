package com.researchspace.webapp.integrations.dsw;

import static com.researchspace.service.IntegrationsHandler.DSW_APP_NAME;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
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
import com.researchspace.webapp.integrations.dsw.exception.DSWVersionCheckException;
import com.researchspace.webapp.integrations.dsw.model.DSWProject;
import com.researchspace.webapp.integrations.dsw.model.DSWProjects;
import com.researchspace.webapp.integrations.dsw.model.DSWUser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.semver4j.Semver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
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
  public static final Semver DSW_API_PATH_CHANGE_VERSION = new Semver("5.0.0");

  /** How long a server's detected paths are reused before its version is checked again. */
  public static final int DSW_API_PATH_CACHE_TTL_MINUTES = 15;

  private static final int DSW_API_PATH_CACHE_MAX_SIZE = 100;

  /**
   * Timeouts for the version check. Concurrent callers for a server wait on one check, so it must
   * not be able to hang.
   */
  public static final Duration DSW_VERSION_CHECK_CONNECT_TIMEOUT = Duration.ofSeconds(5);

  public static final Duration DSW_VERSION_CHECK_READ_TIMEOUT = Duration.ofSeconds(10);

  /**
   * Overall limit on a version check. The read timeout applies to each read and each redirect hop
   * separately, so a server that trickles bytes or chains redirects could otherwise hold every
   * waiter for much longer.
   */
  public static final Duration DSW_VERSION_CHECK_DEADLINE = Duration.ofSeconds(15);

  // Real version info is under 1 KB; anything far larger is some other page.
  private static final int DSW_VERSION_INFO_MAX_BYTES = 64 * 1024;

  /**
   * Most version checks that can run at once. Concurrent checks of one server share a thread, so
   * this is only reached with several servers, or a stalled server whose timed-out checks are still
   * closing; further checks fail at once rather than adding threads.
   */
  public static final int DSW_VERSION_CHECK_MAX_THREADS = 8;

  // The current version check's connection, so that its own thread can close it at the deadline
  // or size limit without the response being drained.
  private static final ThreadLocal<AtomicReference<HttpURLConnection>> VERSION_CHECK_CONNECTION =
      new ThreadLocal<>();

  // Only the start of the reported version is examined; real values such as "v5.0.0~f3c60c086"
  // are much shorter, and the version always comes first. This limit is also what keeps
  // Semver.coerce fast: its time grows much faster than its input (about 11s for 1M digits).
  private static final int DSW_VERSION_STRING_MAX_LENGTH = 32;

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

  /**
   * A server URL and the paths it uses, resolved together so that one operation never mixes the
   * results of two version checks.
   */
  private record DSWServer(String url, DSWPaths paths) {
    String apiBaseUrl() {
      return url + paths.api;
    }

    String projectsBaseUrl() {
      return url + paths.projects;
    }
  }

  private final RestTemplate restTemplate;
  private final RestTemplate versionCheckRestTemplate;
  private final Duration versionCheckDeadline;
  private final ExecutorService versionCheckExecutor = buildVersionCheckExecutor();
  // Server URL, without trailing slash, to the paths it uses.
  private final Cache<String, DSWPaths> pathsCache;
  @Autowired private UserConnectionManager source;
  @Autowired private UserManager userManager;
  @Autowired private MediaManager mediaManager;
  @Autowired private DMPManager dmpManager;

  private ObjectMapper mapper = new ObjectMapper();

  public DSWClient() {
    this(null, null, null, null);
  }

  @Autowired
  public DSWClient(
      UserConnectionManager userConnectionManager,
      UserManager userManager,
      MediaManager mediaManager,
      DMPManager dmpManager) {
    this(
        userConnectionManager,
        userManager,
        mediaManager,
        dmpManager,
        new RestTemplate(),
        buildVersionCheckRestTemplate(
            DSW_VERSION_CHECK_CONNECT_TIMEOUT, DSW_VERSION_CHECK_READ_TIMEOUT),
        Ticker.systemTicker());
  }

  DSWClient(RestTemplate restTemplate, Ticker ticker) {
    this(null, null, null, null, restTemplate, restTemplate, ticker);
  }

  DSWClient(
      UserConnectionManager userConnectionManager,
      UserManager userManager,
      MediaManager mediaManager,
      DMPManager dmpManager,
      RestTemplate restTemplate,
      RestTemplate versionCheckRestTemplate,
      Ticker ticker) {
    this(
        userConnectionManager,
        userManager,
        mediaManager,
        dmpManager,
        restTemplate,
        versionCheckRestTemplate,
        ticker,
        DSW_VERSION_CHECK_DEADLINE);
  }

  DSWClient(
      UserConnectionManager userConnectionManager,
      UserManager userManager,
      MediaManager mediaManager,
      DMPManager dmpManager,
      RestTemplate restTemplate,
      RestTemplate versionCheckRestTemplate,
      Ticker ticker,
      Duration versionCheckDeadline) {
    this.source = userConnectionManager;
    this.userManager = userManager;
    this.mediaManager = mediaManager;
    this.dmpManager = dmpManager;
    this.restTemplate = restTemplate;
    this.versionCheckRestTemplate = versionCheckRestTemplate;
    this.versionCheckDeadline = versionCheckDeadline;
    this.pathsCache = buildPathsCache(ticker);
  }

  // Idle threads end after a minute; shutdown() stops the rest when the bean is destroyed.
  private static ExecutorService buildVersionCheckExecutor() {
    return new ThreadPoolExecutor(
        0,
        DSW_VERSION_CHECK_MAX_THREADS,
        60,
        TimeUnit.SECONDS,
        new SynchronousQueue<>(),
        runnable -> {
          Thread thread = new Thread(runnable, "dsw-version-check");
          thread.setDaemon(true);
          return thread;
        });
  }

  /**
   * Stops the version check threads and interrupts any check in progress, which ends once its
   * current connect or read returns or times out. Spring calls it when the application context
   * closes (see the {@code dsw} bean in BaseConfig).
   */
  public void shutdown() {
    versionCheckExecutor.shutdownNow();
  }

  static RestTemplate buildVersionCheckRestTemplate(Duration connectTimeout, Duration readTimeout) {
    // Same-scheme redirects are followed, so a 5.x server behind host canonicalisation or a
    // moved path is still detected.
    SimpleClientHttpRequestFactory requestFactory =
        new SimpleClientHttpRequestFactory() {
          @Override
          protected void prepareConnection(HttpURLConnection connection, String httpMethod)
              throws IOException {
            super.prepareConnection(connection, httpMethod);
            AtomicReference<HttpURLConnection> holder = VERSION_CHECK_CONNECTION.get();
            if (holder != null) {
              holder.set(connection);
            }
          }
        };
    requestFactory.setConnectTimeout((int) connectTimeout.toMillis());
    requestFactory.setReadTimeout((int) readTimeout.toMillis());
    return new RestTemplate(requestFactory);
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
   * The release version is read from the unauthenticated info endpoint at the new path. If that
   * does not report 5.0.0 or later (a 4xx other than 429, an empty, oversized or non-JSON body, or
   * no version or an older one), the info endpoint at the legacy path is checked as well, so that a
   * proxy or gateway answering 401 or 403 for an unknown path is not on its own taken to mean a
   * pre-5.0.0 server. The legacy paths are used unless a 5.x version is reported; if neither
   * endpoint reports a version, a warning is logged and the legacy paths are used, as before RSpace
   * supported DSW 5.
   *
   * <p>The detected paths, including that last fallback, are cached per server URL for {@link
   * #DSW_API_PATH_CACHE_TTL_MINUTES} minutes. Failures are not cached, so the next call checks
   * again.
   *
   * @throws DSWVersionCheckException from either check for any other failure, such as a timeout,
   *     connection error, 429 or 5xx status, or a redirect that is not followed (such as http to
   *     https), so that a temporary fault on a 5.x server is not mistaken for a pre-5.0.0 server
   */
  String getApiBaseUrl(String savedUrl) {
    return resolveServer(savedUrl).apiBaseUrl();
  }

  /**
   * Base URL of the DSW project pages, which a project UUID is appended to. Uses the same version
   * detection and cache as {@link #getApiBaseUrl(String)}.
   */
  String getProjectsBaseUrl(String savedUrl) {
    return resolveServer(savedUrl).projectsBaseUrl();
  }

  // The stored URL has no trailing slash (see IntegrationsHandlerImpl.saveAppOptions).
  private DSWServer resolveServer(String serverUrl) {
    return new DSWServer(serverUrl, getPaths(serverUrl));
  }

  private DSWServer resolveServer(DSWConnectionConfig connCfg) throws MalformedURLException {
    return resolveServer(connCfg.getRepositoryURL().get().toString());
  }

  private DSWPaths getPaths(String serverUrl) {
    try {
      // Concurrent misses for the same URL share one version check.
      return pathsCache.get(serverUrl, () -> detectPaths(serverUrl));
    } catch (UncheckedExecutionException | ExecutionException e) {
      if (e.getCause() instanceof DSWVersionCheckException versionCheckException) {
        throw versionCheckException;
      }
      if (e.getCause() instanceof RestClientException restClientException) {
        throw new DSWVersionCheckException(
            DSWVersionCheckException.Reason.UNAVAILABLE,
            "Could not check the DSW version at " + serverUrl + ": " + e.getCause().getMessage(),
            restClientException);
      }
      throw new IllegalStateException(
          "Could not detect the DSW paths for " + serverUrl, e.getCause());
    }
  }

  private DSWPaths detectPaths(String serverUrl) {
    // Both checks share one deadline, so that an older server cannot double the wait.
    long deadlineNanos = System.nanoTime() + versionCheckDeadline.toNanos();
    Optional<Semver> version = getServerVersion(serverUrl, DSW_PATH_API_CURRENT, deadlineNanos);
    if (!isCurrent(version)) {
      Optional<Semver> legacyVersion =
          getServerVersion(serverUrl, DSW_PATH_API_LEGACY, deadlineNanos);
      if (isCurrent(legacyVersion)) {
        log.warn(
            "DSW at {} reports version {} at {} but not at {}",
            serverUrl,
            legacyVersion.get(),
            DSW_PATH_API_LEGACY,
            DSW_PATH_API_CURRENT);
      }
      if (legacyVersion.isPresent()) {
        version = legacyVersion;
      }
    }
    DSWPaths paths = isCurrent(version) ? DSWPaths.CURRENT : DSWPaths.LEGACY;
    if (version.isEmpty()) {
      log.warn(
          "DSW at {} reported no version at {} or {}: using API path {} and project path {}",
          serverUrl,
          DSW_PATH_API_CURRENT,
          DSW_PATH_API_LEGACY,
          paths.api,
          paths.projects);
      return paths;
    }
    // Once per server per cache period; getServerVersion logs why each check gave no version.
    log.info(
        "DSW at {} reports version {}: using API path {} and project path {}",
        serverUrl,
        version.get(),
        paths.api,
        paths.projects);
    return paths;
  }

  private static boolean isCurrent(Optional<Semver> version) {
    return version.filter(v -> v.isGreaterThanOrEqualTo(DSW_API_PATH_CHANGE_VERSION)).isPresent();
  }

  private String getApiBaseUrl(DSWConnectionConfig connCfg) throws MalformedURLException {
    return resolveServer(connCfg).apiBaseUrl();
  }

  /** The parts of the version info response that detection uses. */
  private record VersionInfoResponse(
      HttpStatusCode status, String location, String body, boolean tooLarge) {}

  /**
   * Requests the version info at {@code serverUrl + path}. A 4xx other than 429 is returned without
   * its body; a 429 or 5xx is thrown. Runs on the version check executor; stops reading at the
   * deadline or size limit and closes its own connection, since a read blocked in another thread
   * holds a lock that would stall a disconnect from the waiting thread.
   *
   * <p>Uses the request factory directly rather than {@code RestTemplate.execute}: its error
   * handler reads the whole body of a 4xx or 5xx response, with none of these limits.
   */
  private VersionInfoResponse requestVersionInfo(
      String serverUrl, String path, long deadlineNanos) {
    URI uri = UriComponentsBuilder.fromUriString(serverUrl + path).build().toUri();
    try {
      ClientHttpRequest request =
          versionCheckRestTemplate.getRequestFactory().createRequest(uri, HttpMethod.GET);
      request.getHeaders().setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
      try (ClientHttpResponse response = request.execute()) {
        // Read before the body: reading it may disconnect, after which the JDK resets the status.
        HttpStatusCode status = response.getStatusCode();
        if (status.is2xxSuccessful()) {
          String body = readVersionInfoBody(response.getBody(), serverUrl, deadlineNanos);
          return new VersionInfoResponse(status, null, body, body == null);
        }
        // Raw text, only for a redirect: a malformed Location must not fail parsing.
        String location =
            status.is3xxRedirection() ? response.getHeaders().getFirst(HttpHeaders.LOCATION) : null;
        // The body is not needed: close the connection so that closing the response cannot read
        // the rest of it. The stream is opened first so that close() does not open it afterwards.
        response.getBody();
        disconnectVersionCheck();
        if (status.is3xxRedirection()) {
          return new VersionInfoResponse(status, location, null, false);
        }
        // A 429 is temporary; other 4xx statuses, such as a 401 or 403 from a gateway, only say
        // that this endpoint gave no answer.
        if (status.is4xxClientError() && status.value() != HttpStatus.TOO_MANY_REQUESTS.value()) {
          return new VersionInfoResponse(status, null, null, false);
        }
        throw errorFor(status, uri);
      }
    } catch (IOException e) {
      throw new ResourceAccessException(
          "I/O error on GET request for \"" + uri + "\": " + e.getMessage(), e);
    }
  }

  // Same exception types as RestTemplate's error handler, without the response body.
  private static RestClientException errorFor(HttpStatusCode status, URI uri) {
    String message = "HTTP " + status.value() + " from DSW version check at " + uri;
    if (status.is4xxClientError()) {
      return HttpClientErrorException.create(
          message, status, "", new HttpHeaders(), new byte[0], null);
    }
    if (status.is5xxServerError()) {
      return HttpServerErrorException.create(
          message, status, "", new HttpHeaders(), new byte[0], null);
    }
    return new RestClientException(message);
  }

  /** Returns the body as text, or null if it is larger than any real version info. */
  private String readVersionInfoBody(InputStream in, String serverUrl, long deadlineNanos)
      throws IOException {
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int read;
    while ((read = in.read(buffer)) != -1) {
      body.write(buffer, 0, read);
      if (body.size() > DSW_VERSION_INFO_MAX_BYTES) {
        // Close now, so that the response is not drained to the end.
        disconnectVersionCheck();
        return null;
      }
      if (System.nanoTime() - deadlineNanos > 0 || Thread.currentThread().isInterrupted()) {
        disconnectVersionCheck();
        throw new IOException(
            "DSW version check at "
                + serverUrl
                + " did not finish within "
                + versionCheckDeadline.toSeconds()
                + " seconds");
      }
    }
    return body.toString(StandardCharsets.UTF_8);
  }

  private static void disconnectVersionCheck() {
    AtomicReference<HttpURLConnection> holder = VERSION_CHECK_CONNECTION.get();
    if (holder != null && holder.get() != null) {
      holder.get().disconnect();
    }
  }

  /**
   * Runs {@link #requestVersionInfo} with an overall deadline, so that callers waiting on this
   * check are released even if the request is still in progress.
   */
  private VersionInfoResponse requestVersionInfoWithDeadline(
      String serverUrl, String path, long deadlineNanos) {
    Future<VersionInfoResponse> request;
    try {
      request =
          versionCheckExecutor.submit(
              () -> {
                VERSION_CHECK_CONNECTION.set(new AtomicReference<>());
                try {
                  return requestVersionInfo(serverUrl, path, deadlineNanos);
                } finally {
                  VERSION_CHECK_CONNECTION.remove();
                }
              });
    } catch (RejectedExecutionException e) {
      String message =
          String.format(
              "DSW version check at %s not started: %d checks already running, or shutting down",
              serverUrl, DSW_VERSION_CHECK_MAX_THREADS);
      log.warn(message);
      throw new ResourceAccessException(message);
    }
    try {
      return request.get(Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RestClientException restClientException) {
        throw restClientException;
      }
      throw new IllegalStateException(
          "Unexpected error checking the DSW version at " + serverUrl, e.getCause());
    } catch (TimeoutException e) {
      // Waiting callers are released now, but the worker may not stop yet: a blocked connect or
      // read ignores the interrupt. The reading loop sees it when a read returns and closes the
      // connection; otherwise the worker ends when the connect or read timeout expires (for each
      // redirect hop). Until then it counts towards DSW_VERSION_CHECK_MAX_THREADS.
      request.cancel(true);
      String message =
          String.format(
              "DSW version check at %s did not finish within %d seconds",
              serverUrl, versionCheckDeadline.toSeconds());
      log.warn(message);
      throw new ResourceAccessException(message);
    } catch (InterruptedException e) {
      request.cancel(true);
      Thread.currentThread().interrupt();
      log.warn("Interrupted while checking the DSW version at {}", serverUrl);
      throw new ResourceAccessException(
          "Interrupted while checking the DSW version at " + serverUrl);
    }
  }

  /** The version reported at {@code serverUrl + path}, or empty if it gives none. */
  private Optional<Semver> getServerVersion(String serverUrl, String path, long deadlineNanos) {
    String infoUrl = serverUrl + path;
    VersionInfoResponse response = requestVersionInfoWithDeadline(serverUrl, path, deadlineNanos);
    if (response.status().is3xxRedirection()) {
      // Same-scheme redirects are followed, so this is one that changes scheme (such as http to
      // https). The API calls would not follow it either, so the saved URL needs correcting.
      String message =
          String.format(
              "DSW at %s redirects (HTTP %d) to %s, which is not followed; check the saved DSW URL",
              serverUrl,
              response.status().value(),
              forLog(Objects.toString(response.location(), "")));
      log.warn(message);
      throw new DSWVersionCheckException(DSWVersionCheckException.Reason.REDIRECT, message);
    }
    if (response.status().is4xxClientError()) {
      log.debug("No DSW version info at {} (HTTP {})", infoUrl, response.status().value());
      return Optional.empty();
    }
    if (response.tooLarge()) {
      log.debug(
          "DSW version info at {} is larger than {} bytes", infoUrl, DSW_VERSION_INFO_MAX_BYTES);
      return Optional.empty();
    }
    String body = response.body();
    if (body.isBlank()) {
      log.debug("Empty DSW version info at {}", infoUrl);
      return Optional.empty();
    }
    JsonNode info;
    try {
      info = mapper.readTree(body);
    } catch (JsonProcessingException e) {
      log.debug("DSW version info at {} is not JSON", infoUrl);
      return Optional.empty();
    }
    // releaseVersion is "5.0.0"; version is "v5.0.0~f3c60c086"
    String reported = info.path("releaseVersion").asText("");
    if (reported.isBlank()) {
      reported = info.path("version").asText("");
    }
    reported = StringUtils.left(reported, DSW_VERSION_STRING_MAX_LENGTH);
    // Fills in missing parts and ignores a leading "v" and trailing text, so "5" and
    // "v5.1~f3c60c086" are read as 5.0.0 and 5.1.0; returns null for a number too large to parse.
    Semver version = Semver.coerce(reported);
    if (version == null) {
      log.warn(
          "DSW version info at {} has no recognisable version ({})", infoUrl, forLog(reported));
      return Optional.empty();
    }
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
    // Resolve once: a second lookup could repeat the version check after the cache expires, and
    // fail after the file has been saved.
    DSWServer dswServer = resolveServer(connCfg);
    String apiBaseUrl = dswServer.apiBaseUrl();
    String dmpLink =
        UriComponentsBuilder.fromUriString(dswServer.projectsBaseUrl() + planUuid)
            .build()
            .toUri()
            .toString();
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
                    new DmpDto(planUuid, projectName, DMPSource.DSW, null, dmpLink)));
      } else {
        // The link changes when the server is upgraded to 5.x, and the project may be renamed.
        dmpUser.get().setDmpLink(dmpLink);
        dmpUser.get().setTitle(projectName);
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
