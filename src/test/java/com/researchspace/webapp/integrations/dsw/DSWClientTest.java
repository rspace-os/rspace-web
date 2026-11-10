package com.researchspace.webapp.integrations.dsw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.google.common.base.Ticker;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

  private MockRestServiceServer server;
  private FakeTicker ticker;
  private DSWClient client;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
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
  void usesLegacyPathWhenInfoEndpointRedirects() {
    server.expect(requestTo(INFO_URL)).andRespond(withStatus(HttpStatus.FOUND));

    assertEquals(SERVER + "/wizard-api/", client.getApiBaseUrl(SERVER));
  }

  @Test
  void usesLegacyPathWhenJsonContentTypeHasNonJsonBody() {
    server
        .expect(requestTo(INFO_URL))
        .andRespond(withSuccess("<html></html>", MediaType.APPLICATION_JSON));

    assertEquals(SERVER + "/wizard-api/", client.getApiBaseUrl(SERVER));
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
}
