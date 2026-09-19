package com.researchspace.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.testutil.StringAppenderForTestLogging;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.OutputStreamAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

class BioPortalOntologiesClientTest {

  private static final String API_BASE_URL = "https://data.bioontology.org";

  private BioPortalOntologiesClient client;
  private RestTemplate restTemplate;
  private MockRestServiceServer mockServer;

  @BeforeEach
  void setUp() {
    client = new BioPortalOntologiesClient();
    restTemplate = new RestTemplate();
    mockServer = MockRestServiceServer.createServer(restTemplate);
    ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
    ReflectionTestUtils.setField(client, "bioportalApiBaseUrl", API_BASE_URL);
    ReflectionTestUtils.setField(client, "bioportalBaseUrl", "https://bioportal.bioontology.org");
    ReflectionTestUtils.setField(client, "bioportalApiKey", "test-api-key");
  }

  @Test
  void shouldDeserializeSuccessfulResponse() {
    mockServer
        .expect(requestTo(startsWithUri(API_BASE_URL + "/search")))
        .andRespond(withSuccess(singleResultJson(), MediaType.APPLICATION_JSON));

    List<BioPortalSearchResult> results = client.search("Tolstoy");

    assertEquals(1, results.size());
    assertEquals("Lev Tolstoy", results.get(0).getPrefLabel());
    assertEquals("http://purl.obolibrary.org/obo/GAZ_00593210", results.get(0).getId());
    assertEquals(
        "https://data.bioontology.org/ontologies/GAZ", results.get(0).getLinks().getOntology());
  }

  @Test
  void shouldSendAuthorizationHeader() {
    mockServer
        .expect(requestTo(startsWithUri(API_BASE_URL + "/search")))
        .andExpect(header("Authorization", "apikey token=test-api-key"))
        .andRespond(withSuccess(emptyCollectionJson(), MediaType.APPLICATION_JSON));

    client.search("Tolstoy");

    mockServer.verify();
  }

  @Test
  void shouldSendRequiredQueryParameters() {
    mockServer
        .expect(requestTo(startsWithUri(API_BASE_URL + "/search")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(queryParam("q", "Tolstoy"))
        .andExpect(queryParam("suggest", "true"))
        .andExpect(queryParam("pagesize", "20"))
        .andRespond(withSuccess(emptyCollectionJson(), MediaType.APPLICATION_JSON));

    client.search("Tolstoy");

    mockServer.verify();
  }

  @Test
  void shouldSafelyEncodeSpecialCharacterQueries() {
    List<String> terms =
        List.of("heart failure", "A&B", "gene + protein", "alpha/beta", "α-synuclein");
    for (String term : terms) {
      mockServer
          .expect(requestTo(startsWithUri(API_BASE_URL + "/search")))
          .andExpect(decodedQueryParam("q", term))
          .andRespond(withSuccess(emptyCollectionJson(), MediaType.APPLICATION_JSON));
    }

    for (String term : terms) {
      assertTrue(client.search(term).isEmpty());
    }
    mockServer.verify();
  }

  private static RequestMatcher decodedQueryParam(String name, String expectedValue) {
    return request -> {
      String rawValue =
          UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst(name);
      String decoded = rawValue == null ? null : UriUtils.decode(rawValue, StandardCharsets.UTF_8);
      assertEquals(expectedValue, decoded, "query param [" + name + "]");
    };
  }

  @Test
  void shouldReturnEmptyListForEmptyCollection() {
    mockServer
        .expect(requestTo(startsWithUri(API_BASE_URL + "/search")))
        .andRespond(withSuccess(emptyCollectionJson(), MediaType.APPLICATION_JSON));

    assertEquals(0, client.search("Tolstoy").size());
  }

  @Test
  void shouldReturnEmptyListWhenCollectionKeyIsMissingEntirely() {
    mockServer
        .expect(requestTo(startsWithUri(API_BASE_URL + "/search")))
        .andRespond(
            withSuccess(
                "{\"page\":1,\"pageCount\":0,\"totalCount\":0}", MediaType.APPLICATION_JSON));

    assertEquals(0, client.search("Tolstoy").size());
  }

  @Test
  void shouldIgnoreUnknownJsonFields() {
    String json =
        "{\"page\":1,\"pageCount\":1,\"totalCount\":1,\"collection\":[{\"prefLabel\":\"Lev"
            + " Tolstoy\",\"@id\":\"http://purl.obolibrary.org/obo/GAZ_00593210\",\"@type\":\"owl:Class\",\"obsolete\":false,\"links\":{\"ontology\":\"https://data.bioontology.org/ontologies/GAZ\",\"self\":\"https://data.bioontology.org/ignored\"}}]}";
    mockServer
        .expect(requestTo(startsWithUri(API_BASE_URL + "/search")))
        .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

    List<BioPortalSearchResult> results = client.search("Tolstoy");

    assertEquals(1, results.size());
    assertEquals("Lev Tolstoy", results.get(0).getPrefLabel());
  }

  @Test
  void shouldKeepDuplicateLabelsFromDifferentOntologies() {
    String json =
        "{\"collection\":["
            + "{\"prefLabel\":\"Toluene\",\"@id\":\"http://purl.obolibrary.org/obo/CHEAR_1\",\"links\":{\"ontology\":\"https://data.bioontology.org/ontologies/CHEAR\"}},"
            + "{\"prefLabel\":\"Toluene\",\"@id\":\"http://purl.obolibrary.org/obo/HHEAR_1\",\"links\":{\"ontology\":\"https://data.bioontology.org/ontologies/HHEAR\"}}"
            + "]}";
    mockServer
        .expect(requestTo(startsWithUri(API_BASE_URL + "/search")))
        .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

    List<BioPortalSearchResult> results = client.search("Toluene");

    assertEquals(2, results.size());
    assertEquals("CHEAR", lastPathSegment(results.get(0).getLinks().getOntology()));
    assertEquals("HHEAR", lastPathSegment(results.get(1).getLinks().getOntology()));
  }

  @Test
  void shouldPropagateProviderFailure() {
    mockServer
        .expect(requestTo(startsWithUri(API_BASE_URL + "/search")))
        .andRespond(withServerError());

    assertThrows(RestClientException.class, () -> client.search("Tolstoy"));
  }

  @Test
  void shouldCacheSuccessfulResponsesForSameQuery() {
    mockServer
        .expect(requestTo(startsWithUri(API_BASE_URL + "/search")))
        .andRespond(withSuccess(singleResultJson(), MediaType.APPLICATION_JSON));

    List<BioPortalSearchResult> first = client.search("Tolstoy");
    List<BioPortalSearchResult> second = client.search("Tolstoy");

    assertSame(first, second); // same cached instance, so the server was hit only once
    mockServer.verify();
  }

  @Test
  void shouldNotLogRequestDataOnTransportFailure() {
    String searchTerm = "Confidential Falcon compound";
    mockServer
        .expect(requestTo(startsWithUri(API_BASE_URL + "/search")))
        .andRespond(
            request -> {
              IOException failure =
                  new IOException(
                      "Failed request " + request.getURI(),
                      new IOException(
                          "Authorization: "
                              + request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)));
              failure.addSuppressed(new IOException("Provider echoed " + searchTerm));
              throw failure;
            });

    String logs =
        captureFailureLogs(
            () -> assertThrows(RestClientException.class, () -> client.search(searchTerm)));

    assertSafeFailureLog(logs, searchTerm);
    assertTrue(logs.contains("ResourceAccessException"));
    mockServer.verify();
  }

  @Test
  void shouldNotLogResponseBodyOnHttpFailure() {
    String searchTerm = "Confidential Falcon compound";
    mockServer
        .expect(requestTo(startsWithUri(API_BASE_URL + "/search")))
        .andRespond(withServerError().body("Provider echoed " + searchTerm + " test-api-key"));

    String logs =
        captureFailureLogs(
            () -> assertThrows(RestClientException.class, () -> client.search(searchTerm)));

    assertSafeFailureLog(logs, searchTerm);
    assertTrue(logs.contains("status=500"));
    mockServer.verify();
  }

  private static void assertSafeFailureLog(String logs, String searchTerm) {
    assertFalse(logs.contains(searchTerm), logs);
    assertFalse(logs.contains(UriUtils.encodeQueryParam(searchTerm, StandardCharsets.UTF_8)), logs);
    assertFalse(logs.contains("test-api-key"));
    assertFalse(logs.contains("q="));
    assertTrue(logs.contains("WARN"));
    assertTrue(logs.contains("endpoint=" + API_BASE_URL + "/search"));
    assertTrue(logs.contains("at org.springframework.web.client.RestTemplate."));
    assertEquals(1, logs.split("BioPortal search request failed", -1).length - 1);
  }

  private static String captureFailureLogs(Runnable action) {
    Logger logger = (Logger) LogManager.getLogger(BioPortalOntologiesClient.class);
    Level originalLevel = logger.getLevel();
    boolean originalAdditive = logger.isAdditive();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    OutputStreamAppender appender =
        OutputStreamAppender.newBuilder()
            .setName("bioportal-failure-test")
            .setTarget(output)
            .setLayout(
                PatternLayout.newBuilder().withPattern("%level %message%n%throwable").build())
            .build();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.WARN);
    logger.setAdditive(false);
    try {
      action.run();
      return output.toString(StandardCharsets.UTF_8);
    } finally {
      logger.removeAppender(appender);
      appender.stop();
      logger.setLevel(originalLevel);
      logger.setAdditive(originalAdditive);
    }
  }

  @Test
  void shouldShareCacheEntryAcrossDifferentCaseOfSameQuery() {
    mockServer
        .expect(requestTo(startsWithUri(API_BASE_URL + "/search")))
        .andExpect(queryParam("q", "Tolstoy"))
        .andRespond(withSuccess(singleResultJson(), MediaType.APPLICATION_JSON));

    List<BioPortalSearchResult> first = client.search("Tolstoy");
    List<BioPortalSearchResult> second = client.search("tolstoy");

    assertSame(first, second); // same cache entry regardless of case, so only one upstream call
    mockServer.verify();
  }

  @Test
  void shouldReturnUnmodifiableResultList() {
    mockServer
        .expect(requestTo(startsWithUri(API_BASE_URL + "/search")))
        .andRespond(withSuccess(singleResultJson(), MediaType.APPLICATION_JSON));

    List<BioPortalSearchResult> results = client.search("Tolstoy");

    assertThrows(UnsupportedOperationException.class, () -> results.add(null));
  }

  @Test
  void shouldNotShareCacheEntriesAcrossDifferentQueries() {
    mockServer
        .expect(requestTo(startsWithUri(API_BASE_URL + "/search")))
        .andExpect(queryParam("q", "Tolstoy"))
        .andRespond(withSuccess(singleResultJson(), MediaType.APPLICATION_JSON));
    mockServer
        .expect(requestTo(startsWithUri(API_BASE_URL + "/search")))
        .andExpect(queryParam("q", "heart"))
        .andRespond(withSuccess(emptyCollectionJson(), MediaType.APPLICATION_JSON));

    List<BioPortalSearchResult> tolstoy = client.search("Tolstoy");
    List<BioPortalSearchResult> heart = client.search("heart");

    assertEquals(1, tolstoy.size());
    assertEquals(0, heart.size());
    mockServer.verify();
  }

  @Test
  void shouldNotCacheFailedRequests() {
    mockServer
        .expect(requestTo(startsWithUri(API_BASE_URL + "/search")))
        .andRespond(withServerError());
    mockServer
        .expect(requestTo(startsWithUri(API_BASE_URL + "/search")))
        .andRespond(withSuccess(singleResultJson(), MediaType.APPLICATION_JSON));

    assertThrows(RestClientException.class, () -> client.search("Tolstoy"));
    List<BioPortalSearchResult> results = client.search("Tolstoy");

    assertEquals(1, results.size());
    mockServer.verify(); // both requests were made; the failure was not served from cache
  }

  @Test
  void shouldReturnEmptyAndSkipRequestWhenApiKeyMissing() {
    ReflectionTestUtils.setField(client, "bioportalApiKey", "");

    List<BioPortalSearchResult> results = client.search("Tolstoy");

    assertEquals(0, results.size());
    mockServer.verify(); // no expectations were set up, and none should have been requested
  }

  @Test
  void shouldAllowRequestWhenApiBaseUrlIsAllowlistedHttpsHost() {
    mockServer
        .expect(requestTo(startsWithUri(API_BASE_URL + "/search")))
        .andRespond(withSuccess(emptyCollectionJson(), MediaType.APPLICATION_JSON));

    assertTrue(client.search("Tolstoy").isEmpty());
    mockServer.verify();
  }

  @Test
  void shouldReturnEmptyAndSkipRequestWhenApiBaseUrlIsNotHttps() {
    ReflectionTestUtils.setField(client, "bioportalApiBaseUrl", "http://data.bioontology.org");

    List<BioPortalSearchResult> results = client.search("Tolstoy");

    assertEquals(0, results.size());
    mockServer.verify(); // no expectations were set up, and none should have been requested
  }

  @Test
  void shouldReturnEmptyAndSkipRequestWhenApiBaseUrlIsDifferentHost() {
    ReflectionTestUtils.setField(client, "bioportalApiBaseUrl", "https://evil.example");

    List<BioPortalSearchResult> results = client.search("Tolstoy");

    assertEquals(0, results.size());
    mockServer.verify(); // no expectations were set up, and none should have been requested
  }

  @Test
  void shouldReturnEmptyAndSkipRequestWhenApiBaseUrlIsMaliciousSubdomainSuffix() {
    StringAppenderForTestLogging strLog =
        CoreTestUtils.configureStringLogger(LogManager.getLogger(BioPortalOntologiesClient.class));
    // "startsWith" would fall for this; exact host match won't
    ReflectionTestUtils.setField(
        client, "bioportalApiBaseUrl", "https://data.bioontology.org.evil.example");

    List<BioPortalSearchResult> results = client.search("Tolstoy");

    assertEquals(0, results.size());
    mockServer.verify(); // no expectations were set up, and none should have been requested
    assertTrue(strLog.logContents.contains("https://data.bioontology.org.evil.example"));
    assertTrue(strLog.logContents.contains("data.bioontology.org"));
  }

  @Test
  void shouldReturnEmptyAndSkipRequestWhenApiBaseUrlIsBlank() {
    StringAppenderForTestLogging strLog =
        CoreTestUtils.configureStringLogger(LogManager.getLogger(BioPortalOntologiesClient.class));
    ReflectionTestUtils.setField(client, "bioportalApiBaseUrl", "");

    List<BioPortalSearchResult> results = client.search("Tolstoy");

    assertEquals(0, results.size());
    mockServer.verify(); // no expectations were set up, and none should have been requested
    assertTrue(strLog.logContents.contains("BioPortal API base URL is not configured"));
  }

  @Test
  void shouldCanonicalizeAwayNonDefaultPortOnAllowlistedHost() {
    ReflectionTestUtils.setField(
        client, "bioportalApiBaseUrl", "https://data.bioontology.org:8443");
    mockServer
        .expect(requestTo(startsWithUri(API_BASE_URL + "/search")))
        .andExpect(request -> assertEquals(-1, request.getURI().getPort(), "request port"))
        .andRespond(withSuccess(emptyCollectionJson(), MediaType.APPLICATION_JSON));

    assertTrue(client.search("Tolstoy").isEmpty());
    mockServer.verify();
  }

  @Test
  void shouldReturnEmptyAndSkipRequestWhenApiBaseUrlIsMalformed() {
    StringAppenderForTestLogging strLog =
        CoreTestUtils.configureStringLogger(LogManager.getLogger(BioPortalOntologiesClient.class));
    ReflectionTestUtils.setField(client, "bioportalApiBaseUrl", "not a url");

    List<BioPortalSearchResult> results = client.search("Tolstoy");

    assertEquals(0, results.size());
    mockServer.verify(); // no expectations were set up, and none should have been requested

    assertTrue(strLog.logContents.contains("not a url"));
    assertTrue(strLog.logContents.contains("is not a valid URI"));
  }

  private static String lastPathSegment(String url) {
    return url.substring(url.lastIndexOf('/') + 1);
  }

  private static Matcher<String> startsWithUri(String prefix) {
    return Matchers.startsWith(prefix);
  }

  private static String singleResultJson() {
    return "{\"page\":1,\"pageCount\":1,\"totalCount\":1,\"collection\":[{\"prefLabel\":\"Lev"
               + " Tolstoy\",\"@id\":\"http://purl.obolibrary.org/obo/GAZ_00593210\",\"links\":{\"ontology\":\"https://data.bioontology.org/ontologies/GAZ\"}}]}";
  }

  private static String emptyCollectionJson() {
    return "{\"page\":1,\"pageCount\":0,\"totalCount\":0,\"collection\":[]}";
  }
}
