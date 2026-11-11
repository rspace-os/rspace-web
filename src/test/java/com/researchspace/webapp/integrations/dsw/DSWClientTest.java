package com.researchspace.webapp.integrations.dsw;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.google.common.base.Ticker;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.apps.AppConfigElement;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.service.DMPManager;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.MediaManager;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.UserManager;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class DSWClientTest {

  private static final String SERVER = "https://dsw.example.com";
  private static final String INFO_URL = SERVER + "/api/";

  private static final String V5_INFO = "{\"releaseVersion\":\"5.0.0\"}";

  private static final class FakeTicker extends Ticker {
    private final AtomicLong nanos = new AtomicLong();

    @Override
    public long read() {
      return nanos.get();
    }

    void advanceMinutes(long minutes) {
      nanos.addAndGet(TimeUnit.MINUTES.toNanos(minutes));
    }
  }

  private RestTemplate restTemplate;
  private MockRestServiceServer server;
  private FakeTicker ticker;
  private DSWClient client;

  @BeforeEach
  void setUp() {
    restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    ticker = new FakeTicker();
    client = new DSWClient(restTemplate, ticker);
  }

  @ParameterizedTest
  @CsvSource({
    "'{\"releaseVersion\":\"5.0.0\",\"version\":\"v5.0.0~f3c60c086\"}', /api/",
    "'{\"releaseVersion\":\"5.1.2\"}', /api/",
    "'{\"version\":\"v5.0.0~f3c60c086\"}', /api/",
    "'{\"releaseVersion\":\"5.1\"}', /api/",
    "'{\"releaseVersion\":\"5.0\"}', /api/",
    "'{\"version\":\"v5.1~f3c60c086\"}', /api/",
    "'{\"releaseVersion\":\"\",\"version\":\"v5.0.0~f3c60c086\"}', /api/",
    "'{\"releaseVersion\":\"4.26.0\"}', /wizard-api/",
    "'{\"releaseVersion\":\"4.26\"}', /wizard-api/",
    "'{\"releaseVersion\":\"5\"}', /wizard-api/",
    "'{\"version\":\"4.9.1\"}', /wizard-api/",
    "'{\"name\":\"Engine Wizard\"}', /wizard-api/"
  })
  void choosesApiPathFromReportedVersion(String infoJson, String expectedPath) {
    server
        .expect(requestTo(INFO_URL))
        .andRespond(withSuccess(infoJson, MediaType.APPLICATION_JSON));

    assertEquals(SERVER + expectedPath, client.getApiBaseUrl(SERVER));
    server.verify();
  }

  @Test
  void usesLegacyPathWhenInfoEndpointIsMissing() {
    server.expect(requestTo(INFO_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertEquals(SERVER + "/wizard-api/", client.getApiBaseUrl(SERVER));
  }

  @Test
  void usesLegacyPathWhenInfoEndpointReturnsHtml() {
    server
        .expect(requestTo(INFO_URL))
        .andRespond(withSuccess("<html></html>", MediaType.TEXT_HTML));

    assertEquals(SERVER + "/wizard-api/", client.getApiBaseUrl(SERVER));
  }

  @ParameterizedTest
  @CsvSource({
    "https://dsw.example.com, https://dsw.example.com",
    "https://dsw.example.com/, https://dsw.example.com",
    "https://dsw.example.com//, https://dsw.example.com",
    "https://example.org/dsw/, https://example.org/dsw"
  })
  void dropsTrailingSlashFromSavedUrl(String savedUrl, String expected) {
    assertEquals(expected, DSWClient.withoutTrailingSlash(savedUrl));
  }

  @Test
  void savedUrlWithTrailingSlashGivesSingleSlashBeforeApiPath() {
    server.expect(requestTo(INFO_URL)).andRespond(withSuccess(V5_INFO, MediaType.APPLICATION_JSON));

    assertEquals(SERVER + "/api/", client.getApiBaseUrl(SERVER + "/"));
  }

  @Test
  void throwsWithoutCachingWhenRedirectIsNotFollowed() {
    String httpServer = "http://dsw.example.com";
    server
        .expect(requestTo(httpServer + "/api/"))
        .andRespond(withStatus(HttpStatus.MOVED_PERMANENTLY).location(URI.create(INFO_URL)));
    server
        .expect(requestTo(httpServer + "/api/"))
        .andRespond(withSuccess(V5_INFO, MediaType.APPLICATION_JSON));

    ResourceAccessException failure =
        assertThrows(ResourceAccessException.class, () -> client.getApiBaseUrl(httpServer));
    assertThat(failure.getMessage()).contains(INFO_URL).contains("check the saved DSW URL");
    assertEquals(httpServer + "/api/", client.getApiBaseUrl(httpServer));
    server.verify();
  }

  @Test
  void usesLegacyPathWhenJsonContentTypeHasNonJsonBody() {
    server
        .expect(requestTo(INFO_URL))
        .andRespond(withSuccess("<html></html>", MediaType.APPLICATION_JSON));

    assertEquals(SERVER + "/wizard-api/", client.getApiBaseUrl(SERVER));
  }

  @Test
  void usesLegacyPathWhenInfoEndpointReturnsOversizedBody() {
    String largePage = "<html>" + " ".repeat(100 * 1024) + "</html>";
    server.expect(requestTo(INFO_URL)).andRespond(withSuccess(largePage, MediaType.TEXT_HTML));

    assertEquals(SERVER + "/wizard-api/", client.getApiBaseUrl(SERVER));
  }

  @Test
  void ignoresMalformedLocationHeaderOnNonRedirect() {
    server
        .expect(requestTo(INFO_URL))
        .andRespond(
            withSuccess(V5_INFO, MediaType.APPLICATION_JSON)
                .header("Location", "http://bad host/"));

    assertEquals(INFO_URL, client.getApiBaseUrl(SERVER));
  }

  @Test
  void reportsMalformedLocationOnUnfollowedRedirectAsRestClientException() {
    server
        .expect(requestTo(INFO_URL))
        .andRespond(
            withStatus(HttpStatus.MOVED_PERMANENTLY).header("Location", "http://bad host/"));

    ResourceAccessException failure =
        assertThrows(ResourceAccessException.class, () -> client.getApiBaseUrl(SERVER));
    assertThat(failure.getMessage()).contains("http://bad host/");
  }

  @Test
  void throwsRatherThanFallingBackOnServerError() {
    server.expect(requestTo(INFO_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    assertThrows(HttpServerErrorException.class, () -> client.getApiBaseUrl(SERVER));
  }

  @Test
  void throwsRatherThanFallingBackOnClientErrorOtherThanNotFound() {
    server.expect(requestTo(INFO_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

    assertThrows(HttpClientErrorException.class, () -> client.getApiBaseUrl(SERVER));
  }

  @Test
  void throwsRatherThanFallingBackWhenServerUnreachable() {
    server
        .expect(requestTo(INFO_URL))
        .andRespond(
            request -> {
              throw new IOException("Connection refused");
            });

    assertThrows(ResourceAccessException.class, () -> client.getApiBaseUrl(SERVER));
  }

  @Test
  void reusesDetectedPathForSameServerWithinTtl() {
    server.expect(requestTo(INFO_URL)).andRespond(withSuccess(V5_INFO, MediaType.APPLICATION_JSON));

    assertEquals(INFO_URL, client.getApiBaseUrl(SERVER));
    ticker.advanceMinutes(DSWClient.DSW_API_PATH_CACHE_TTL_MINUTES - 1);
    assertEquals(INFO_URL, client.getApiBaseUrl(SERVER + "/"));
    server.verify();
  }

  @Test
  void checksVersionAgainAfterTtl() {
    server.expect(requestTo(INFO_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
    server.expect(requestTo(INFO_URL)).andRespond(withSuccess(V5_INFO, MediaType.APPLICATION_JSON));

    assertEquals(SERVER + "/wizard-api/", client.getApiBaseUrl(SERVER));
    ticker.advanceMinutes(DSWClient.DSW_API_PATH_CACHE_TTL_MINUTES);
    assertEquals(INFO_URL, client.getApiBaseUrl(SERVER));
    server.verify();
  }

  @Test
  void cachesEachServerSeparately() {
    String otherServer = "https://other-dsw.example.com";
    server.expect(requestTo(INFO_URL)).andRespond(withSuccess(V5_INFO, MediaType.APPLICATION_JSON));
    server.expect(requestTo(otherServer + "/api/")).andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertEquals(INFO_URL, client.getApiBaseUrl(SERVER));
    assertEquals(otherServer + "/wizard-api/", client.getApiBaseUrl(otherServer));
    server.verify();
  }

  @Test
  void doesNotCacheFailures() {
    server.expect(requestTo(INFO_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    server.expect(requestTo(INFO_URL)).andRespond(withSuccess(V5_INFO, MediaType.APPLICATION_JSON));

    assertThrows(HttpServerErrorException.class, () -> client.getApiBaseUrl(SERVER));
    assertEquals(INFO_URL, client.getApiBaseUrl(SERVER));
    server.verify();
  }

  @Test
  void usesCurrentProjectsPathForV5Server() {
    server.expect(requestTo(INFO_URL)).andRespond(withSuccess(V5_INFO, MediaType.APPLICATION_JSON));

    assertEquals(SERVER + "/projects/", client.getProjectsBaseUrl(SERVER + "/"));
  }

  @Test
  void usesLegacyProjectsPathForPreV5Server() {
    server.expect(requestTo(INFO_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertEquals(SERVER + "/wizard/projects/", client.getProjectsBaseUrl(SERVER));
  }

  @Test
  void apiAndProjectsPathsShareOneVersionCheck() {
    server.expect(requestTo(INFO_URL)).andRespond(withSuccess(V5_INFO, MediaType.APPLICATION_JSON));

    assertEquals(INFO_URL, client.getApiBaseUrl(SERVER));
    assertEquals(SERVER + "/projects/", client.getProjectsBaseUrl(SERVER));
    server.verify();
  }

  @Test
  void stalledVersionCheckTimesOutForAllWaitersAndIsNotCached() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService serverThreads = Executors.newCachedThreadPool();
    HttpServer dsw =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    dsw.createContext(
        "/api/",
        exchange -> {
          try {
            release.await(10, TimeUnit.SECONDS);
            byte[] body = V5_INFO.getBytes(UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
              out.write(body);
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    dsw.setExecutor(serverThreads);
    dsw.start();
    String dswUrl = "http://localhost:" + dsw.getAddress().getPort();
    DSWClient timingOutClient =
        new DSWClient(
            null,
            null,
            null,
            null,
            restTemplate,
            DSWClient.buildVersionCheckRestTemplate(Duration.ofSeconds(1), Duration.ofMillis(300)),
            ticker);
    try {
      CompletableFuture<String> first =
          CompletableFuture.supplyAsync(() -> timingOutClient.getApiBaseUrl(dswUrl));
      CompletableFuture<String> second =
          CompletableFuture.supplyAsync(() -> timingOutClient.getApiBaseUrl(dswUrl));

      for (CompletableFuture<String> caller : new CompletableFuture[] {first, second}) {
        CompletionException failure =
            assertTimeoutPreemptively(
                Duration.ofSeconds(5), () -> assertThrows(CompletionException.class, caller::join));
        assertInstanceOf(ResourceAccessException.class, failure.getCause());
      }

      release.countDown();
      assertEquals(dswUrl + "/api/", timingOutClient.getApiBaseUrl(dswUrl));
    } finally {
      release.countDown();
      dsw.stop(0);
      serverThreads.shutdownNow();
    }
  }

  @Test
  void versionCheckFollowsSameSchemeRedirectToV5Server() throws Exception {
    HttpServer dsw =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    dsw.createContext(
        "/api/",
        exchange -> {
          exchange.getResponseHeaders().add("Location", "/canonical/api/");
          exchange.sendResponseHeaders(301, -1);
          exchange.close();
        });
    dsw.createContext(
        "/canonical/api/",
        exchange -> {
          byte[] body = V5_INFO.getBytes(UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
    dsw.start();
    String dswUrl = "http://localhost:" + dsw.getAddress().getPort();
    DSWClient redirectedClient =
        new DSWClient(
            null,
            null,
            null,
            null,
            restTemplate,
            DSWClient.buildVersionCheckRestTemplate(Duration.ofSeconds(5), Duration.ofSeconds(5)),
            ticker);
    try {
      assertEquals(dswUrl + "/api/", redirectedClient.getApiBaseUrl(dswUrl));
    } finally {
      dsw.stop(0);
    }
  }

  @Test
  void versionCheckDeadlineStopsTrickledResponseAndClosesConnection() throws Exception {
    CountDownLatch clientDisconnected = new CountDownLatch(1);
    HttpServer dsw =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    dsw.createContext(
        "/api/",
        exchange -> {
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, 0);
          OutputStream out = exchange.getResponseBody();
          try {
            // Each byte arrives well within the read timeout, so only the deadline can stop it.
            for (int i = 0; i < 100; i++) {
              out.write(' ');
              out.flush();
              Thread.sleep(100);
            }
          } catch (IOException e) {
            clientDisconnected.countDown();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    ExecutorService serverThreads = Executors.newCachedThreadPool();
    dsw.setExecutor(serverThreads);
    dsw.start();
    String dswUrl = "http://localhost:" + dsw.getAddress().getPort();
    DSWClient deadlineClient =
        new DSWClient(
            null,
            null,
            null,
            null,
            restTemplate,
            DSWClient.buildVersionCheckRestTemplate(Duration.ofSeconds(1), Duration.ofSeconds(1)),
            ticker,
            Duration.ofSeconds(1));
    try {
      ResourceAccessException failure =
          assertTimeoutPreemptively(
              Duration.ofSeconds(5),
              () ->
                  assertThrows(
                      ResourceAccessException.class, () -> deadlineClient.getApiBaseUrl(dswUrl)));
      assertThat(failure.getMessage()).contains("did not finish within 1 seconds");
      assertTrue(clientDisconnected.await(5, TimeUnit.SECONDS));
    } finally {
      dsw.stop(0);
      serverThreads.shutdownNow();
    }
  }

  @Test
  void usesLegacyPathWhenRealConnectionReturnsOversizedBody() throws Exception {
    AtomicInteger requests = new AtomicInteger();
    HttpServer dsw =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    dsw.createContext(
        "/api/",
        exchange -> {
          requests.incrementAndGet();
          byte[] body = ("<html>" + " ".repeat(100 * 1024) + "</html>").getBytes(UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "text/html");
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
          } catch (IOException e) {
            // Expected: the client closes the connection once the size limit is passed.
          }
        });
    dsw.start();
    String dswUrl = "http://localhost:" + dsw.getAddress().getPort();
    DSWClient realConnectionClient =
        new DSWClient(
            null,
            null,
            null,
            null,
            restTemplate,
            DSWClient.buildVersionCheckRestTemplate(Duration.ofSeconds(5), Duration.ofSeconds(5)),
            ticker);
    try {
      assertEquals(dswUrl + "/wizard-api/", realConnectionClient.getApiBaseUrl(dswUrl));
      // Reading the status after disconnect() makes the JDK silently send the request again.
      assertEquals(1, requests.get());
    } finally {
      dsw.stop(0);
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class ImportPlan {

    private static final String ALIAS = "dsw";
    private static final String PLAN_UUID = "22222222-2222-2222-2222-222222222222";

    @Mock private UserConnectionManager userConnectionManager;
    @Mock private UserManager userManager;
    @Mock private MediaManager mediaManager;
    @Mock private DMPManager dmpManager;
    @Mock private AppConfigElementSet cfg;
    @Mock private AppConfigElement urlElement;
    @Mock private UserAppConfig userAppConfig;
    @Mock private User user;
    @Mock private UserConnection userConnection;
    @Mock private EcatDocumentFile savedFile;

    private DSWClient importingClient;

    @BeforeEach
    void setUp() {
      importingClient =
          new DSWClient(
              userConnectionManager,
              userManager,
              mediaManager,
              dmpManager,
              restTemplate,
              restTemplate,
              ticker);
      when(cfg.findElementByPropertyName(DSWClient.DSW_URL)).thenReturn(urlElement);
      when(urlElement.getValue()).thenReturn(SERVER);
      when(cfg.getUserAppConfig()).thenReturn(userAppConfig);
      when(userAppConfig.getUser()).thenReturn(user);
      when(userManager.getAuthenticatedUserInSession()).thenReturn(user);
      when(user.getUsername()).thenReturn("rspaceUser");
      when(userConnectionManager.findByUserNameProviderName(
              "rspaceUser", IntegrationsHandler.DSW_APP_NAME, ALIAS))
          .thenReturn(Optional.of(userConnection));
      when(userConnection.getAccessToken()).thenReturn("token");
    }

    @Test
    void usesOneVersionCheckEvenIfCacheExpiresDuringSave() throws Exception {
      server
          .expect(requestTo(INFO_URL))
          .andRespond(withSuccess(V5_INFO, MediaType.APPLICATION_JSON));
      server
          .expect(requestTo(SERVER + "/api/users/current"))
          .andRespond(withSuccess("{\"uuid\":\"u1\"}", MediaType.APPLICATION_JSON));
      server
          .expect(requestTo(SERVER + "/api/projects?userUuids=u1"))
          .andRespond(
              withSuccess(
                  "{\"_embedded\":{\"projects\":[{\"uuid\":\""
                      + PLAN_UUID
                      + "\",\"description\":\"d\"}]}}",
                  MediaType.APPLICATION_JSON));
      server
          .expect(requestTo(SERVER + "/api/projects/" + PLAN_UUID + "/questionnaire"))
          .andRespond(withSuccess("{\"name\":\"Plan\"}", MediaType.APPLICATION_JSON));
      // MockRestServiceServer fails on any unexpected request, such as a second version check.
      when(mediaManager.saveNewDMPWithDescription(
              eq("Plan"), any(InputStream.class), eq(user), isNull(), eq("d")))
          .thenAnswer(
              invocation -> {
                ticker.advanceMinutes(DSWClient.DSW_API_PATH_CACHE_TTL_MINUTES);
                return savedFile;
              });
      when(dmpManager.findByDmpId(PLAN_UUID, user)).thenReturn(Optional.empty());

      importingClient.importPlan(ALIAS, cfg, PLAN_UUID);

      server.verify();
      ArgumentCaptor<DMPUser> saved = ArgumentCaptor.forClass(DMPUser.class);
      verify(dmpManager).save(saved.capture());
      assertEquals(SERVER + "/projects/" + PLAN_UUID, saved.getValue().getDmpLink());
    }
  }
}
