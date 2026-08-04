package com.researchspace.webapp.integrations.b2inst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.researchspace.b2inst.model.metadata.B2instInstrumentMetadata;
import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.b2inst.model.response.B2instDraftRecord;
import com.researchspace.model.system.SystemProperty;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.SystemPropertyManager;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class B2instConnectorImplTest {

  private static final String REVIEW_URL =
      "https://b2inst-test.gwdg.de/api/records/k2j9p-7yh21/draft/review";

  @Mock private SystemPropertyManager mockSysPropMgr;
  @InjectMocks private B2instConnectorImpl connector;

  private Map<String, SystemPropertyValue> props;

  @BeforeEach
  void setUp() {
    props = new HashMap<>();
    addProperty("pidinst.b2inst.enabled", "true");
    addProperty("pidinst.b2inst.server.url", "https://b2inst-test.gwdg.de");
    addProperty("pidinst.b2inst.community.id", "2cd7e6c2-comm");
    addProperty("pidinst.b2inst.token", "TOK123");
    when(mockSysPropMgr.getAllSysadminPropertiesAsMap()).thenReturn(props);
  }

  private void addProperty(String name, String value) {
    props.put(name, new SystemPropertyValue(new SystemProperty(null), value));
  }

  private B2instDoi draftWithName(String name) {
    B2instInstrumentMetadata md = new B2instInstrumentMetadata();
    md.setName(name);
    B2instDoi doi = new B2instDoi();
    doi.setMetadata(md);
    return doi;
  }

  @Test
  void isConfiguredAndEnabledTrueWhenEnabledAndServerAndTokenPresent() {
    connector.reloadClient();
    assertTrue(connector.isConfiguredAndEnabled());
  }

  @Test
  void isConfiguredAndEnabledFalseWhenDisabled() {
    addProperty("pidinst.b2inst.enabled", "false");
    connector.reloadClient();
    assertFalse(connector.isConfiguredAndEnabled());
  }

  @Test
  void isConfiguredAndEnabledFalseWhenTokenMissing() {
    addProperty("pidinst.b2inst.token", "");
    connector.reloadClient();
    assertFalse(connector.isConfiguredAndEnabled());
  }

  /**
   * Every B2INST call happens inside an open transaction, so an unbounded wait pins a pooled JDBC
   * connection for as long as the socket lives. This has to be asserted on the real request
   * factory: {@code MockRestServiceServer.bindTo} replaces the factory, so no test that binds a
   * mock server can see these bounds.
   */
  @Test
  void restTemplateBoundsConnectAndReadTimeouts() {
    connector.reloadClient();

    SimpleClientHttpRequestFactory factory = underlyingFactory(connector.getRestTemplate());

    assertEquals(10_000, ReflectionTestUtils.getField(factory, "connectTimeout"));
    assertEquals(30_000, ReflectionTestUtils.getField(factory, "readTimeout"));
  }

  /**
   * The bearer-token interceptor makes RestTemplate wrap the configured factory in an {@code
   * InterceptingClientHttpRequestFactory}, so {@code getRequestFactory()} returns the wrapper
   * rather than the factory carrying the timeouts. Unwrap until the real one is reached.
   */
  private SimpleClientHttpRequestFactory underlyingFactory(RestTemplate template) {
    Object factory = template.getRequestFactory();
    for (int depth = 0;
        depth < 5 && !(factory instanceof SimpleClientHttpRequestFactory);
        depth++) {
      factory = ReflectionTestUtils.getField(factory, "requestFactory");
    }
    assertInstanceOf(
        SimpleClientHttpRequestFactory.class,
        factory,
        "expected a SimpleClientHttpRequestFactory somewhere in the chain");
    return (SimpleClientHttpRequestFactory) factory;
  }

  @Test
  void registerDoiPostsCreateRecordWithBearerTokenAndReturnsRid() {
    connector.reloadClient();
    MockRestServiceServer server =
        MockRestServiceServer.bindTo(connector.getRestTemplate()).build();
    server
        .expect(requestTo("https://b2inst-test.gwdg.de/api/records"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer TOK123"))
        .andExpect(jsonPath("$.metadata.Name").value("Microscope X"))
        .andRespond(
            withSuccess(
                "{\"id\":\"k2j9p-7yh21\",\"status\":\"draft\",\"is_draft\":true,"
                    + "\"links\":{\"self\":\"https://b2inst-test.gwdg.de/api/records/k2j9p-7yh21\","
                    + "\"self_html\":\"https://b2inst-test.gwdg.de/uploads/k2j9p-7yh21\"}}",
                MediaType.APPLICATION_JSON));

    B2instDraftRecord created = connector.registerDoi(draftWithName("Microscope X"));

    assertEquals("k2j9p-7yh21", created.getId());
    /*
     * The whole providerUrl feature hangs off @JsonProperty("self_html") on B2instRecordLinks, which
     * lives in rspace-core-model. Nothing else parses it: without this assertion a rename there (or a
     * key change at B2INST) would leave providerUrl silently null with every test, including the
     * MVCIT, still green, because the MVCIT's connector dummy fabricates the value by hand.
     */
    assertNotNull(created.getLinks());
    assertEquals(
        "https://b2inst-test.gwdg.de/uploads/k2j9p-7yh21", created.getLinks().getSelfHtml());
    server.verify();
  }

  @Test
  void registerDoiWrapsTransportErrorInB2instConnectionException() {
    connector.reloadClient();
    MockRestServiceServer server =
        MockRestServiceServer.bindTo(connector.getRestTemplate()).build();
    server
        .expect(requestTo("https://b2inst-test.gwdg.de/api/records"))
        .andRespond(withServerError());

    assertThrows(B2instConnectionException.class, () -> connector.registerDoi(draftWithName("X")));
  }

  @Test
  void testConnectionTrueOn2xx() {
    connector.reloadClient();
    MockRestServiceServer server =
        MockRestServiceServer.bindTo(connector.getRestTemplate()).build();
    server
        .expect(requestTo("https://b2inst-test.gwdg.de/api/communities"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertTrue(connector.testConnection());
  }

  @Test
  void testConnectionFalseOnError() {
    connector.reloadClient();
    MockRestServiceServer server =
        MockRestServiceServer.bindTo(connector.getRestTemplate()).build();
    server
        .expect(requestTo("https://b2inst-test.gwdg.de/api/communities"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertFalse(connector.testConnection());
  }

  @Test
  void deleteDoiDeletesTheDraftByRid() {
    connector.reloadClient();
    MockRestServiceServer server =
        MockRestServiceServer.bindTo(connector.getRestTemplate()).build();
    server
        .expect(requestTo("https://b2inst-test.gwdg.de/api/records/k2j9p-7yh21/draft"))
        .andExpect(method(HttpMethod.DELETE))
        .andExpect(header("Authorization", "Bearer TOK123"))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));

    assertTrue(connector.deleteDoi("k2j9p-7yh21"));
    server.verify();
  }

  /**
   * Publishing now reads the record's current review before creating one, so every publish test has
   * to say what that read finds. 404 is the ordinary case: no review yet.
   */
  private void expectNoExistingReview(MockRestServiceServer server) {
    server
        .expect(requestTo(REVIEW_URL))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));
  }

  @Test
  void publishDoiCreatesReviewThenPostsSubmitAction() {
    connector.reloadClient();
    MockRestServiceServer server =
        MockRestServiceServer.bindTo(connector.getRestTemplate()).build();
    expectNoExistingReview(server);
    String submitUrl = "https://b2inst-test.gwdg.de/api/requests/REQ-1/actions/submit";
    server
        .expect(requestTo(REVIEW_URL))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.receiver.community").value("2cd7e6c2-comm"))
        .andExpect(jsonPath("$.type").value("community-submission"))
        .andRespond(
            withSuccess(
                "{\"status\":\"created\",\"links\":{\"actions\":{\"submit\":\""
                    + submitUrl
                    + "\"}}}",
                MediaType.APPLICATION_JSON));
    // Invenio's action endpoints reject a request without an application/json Content-Type
    // ("Invalid 'Content-Type' header. Expected one of: application/json"), so the submit POST
    // must carry an explicit JSON body rather than an empty one.
    server
        .expect(requestTo(submitUrl))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().string("{}"))
        .andRespond(withSuccess("{\"status\":\"submitted\"}", MediaType.APPLICATION_JSON));

    assertEquals("submitted", connector.publishDoi("k2j9p-7yh21").getStatus());
    server.verify();
  }

  /**
   * Submitting is two calls, so a response lost between them leaves B2INST holding a submitted
   * review while RSpace still records a draft. Re-PUTting that record is answered with {@code 400
   * "An open review cannot be deleted."} (verified against b2inst-test.gwdg.de), which used to
   * wedge the identifier permanently. The retry must instead report the status B2INST already has.
   */
  @Test
  void publishDoiReportsAnAlreadyOpenReviewInsteadOfCreatingASecondOne() {
    connector.reloadClient();
    MockRestServiceServer server =
        MockRestServiceServer.bindTo(connector.getRestTemplate()).build();
    server
        .expect(requestTo(REVIEW_URL))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"id\":\"REQ-1\",\"status\":\"submitted\",\"is_open\":true}",
                MediaType.APPLICATION_JSON));
    // no PUT and no submit POST are expected: server.verify() fails if either is attempted

    assertEquals("submitted", connector.publishDoi("k2j9p-7yh21").getStatus());
    server.verify();
  }

  /**
   * A closed review (declined, cancelled, expired) must not short-circuit, or a rejected record
   * could never be resubmitted. Only an open one blocks a fresh PUT.
   */
  @Test
  void publishDoiCreatesAFreshReviewWhenThePreviousOneIsClosed() {
    connector.reloadClient();
    MockRestServiceServer server =
        MockRestServiceServer.bindTo(connector.getRestTemplate()).build();
    String submitUrl = "https://b2inst-test.gwdg.de/api/requests/REQ-2/actions/submit";
    server
        .expect(requestTo(REVIEW_URL))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"id\":\"REQ-1\",\"status\":\"declined\",\"is_open\":false,\"is_closed\":true}",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(REVIEW_URL))
        .andExpect(method(HttpMethod.PUT))
        .andRespond(
            withSuccess(
                "{\"status\":\"created\",\"links\":{\"actions\":{\"submit\":\""
                    + submitUrl
                    + "\"}}}",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(submitUrl))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{\"status\":\"submitted\"}", MediaType.APPLICATION_JSON));

    assertEquals("submitted", connector.publishDoi("k2j9p-7yh21").getStatus());
    server.verify();
  }

  /**
   * The reason is interpolated into a localized, user-facing message, so it must not carry internal
   * deployment detail. The developer message keeps the property name for the logs. This is the site
   * that previously supplied no reason at all and so leaked the whole message, property name
   * included, to any user who pressed Publish on a half-configured instance.
   */
  /*
   * describeFailure's return value is the exception's reason, and the reason is interpolated into
   * user-facing localized text, so the bearer token must not survive any branch of it. A gateway or
   * WAF that reflects request headers into its error body is what puts it there. The two tests below
   * drive the two branches that can actually carry it: the parsed provider description and the
   * transport-error message. The third branch, the HTTP-status fallback, composes only a status code
   * and a fixed reason phrase, so it cannot carry the token and a test for it would pass with or
   * without the redaction; the single redacted exit still covers it defensively.
   */
  @Test
  void providerErrorMessageIsRedactedBeforeItReachesTheReason() {
    connector.reloadClient();
    MockRestServiceServer server =
        MockRestServiceServer.bindTo(connector.getRestTemplate()).build();
    server
        .expect(requestTo("https://b2inst-test.gwdg.de/api/records"))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":400,\"message\":\"rejected Authorization: Bearer TOK123\"}"));

    B2instConnectionException thrown =
        assertThrows(
            B2instConnectionException.class, () -> connector.registerDoi(draftWithName("X")));

    assertFalse(thrown.getReason().contains("TOK123"), "the token must not reach the reason");
    assertTrue(thrown.getReason().contains("***"), "it should be redacted, not dropped");
    assertFalse(thrown.getMessage().contains("TOK123"), "nor the logged message");
  }

  @Test
  void transportErrorMessageIsRedactedBeforeItReachesTheReason() {
    connector.reloadClient();
    MockRestServiceServer server =
        MockRestServiceServer.bindTo(connector.getRestTemplate()).build();
    server
        .expect(requestTo("https://b2inst-test.gwdg.de/api/records"))
        .andRespond(withException(new IOException("proxy echoed Bearer TOK123")));

    B2instConnectionException thrown =
        assertThrows(
            B2instConnectionException.class, () -> connector.registerDoi(draftWithName("X")));

    assertFalse(thrown.getReason().contains("TOK123"));
  }

  @Test
  void publishDoiThrowsWhenNoCommunityConfigured() {
    addProperty("pidinst.b2inst.community.id", "");
    connector.reloadClient();

    B2instConnectionException thrown =
        assertThrows(B2instConnectionException.class, () -> connector.publishDoi("k2j9p-7yh21"));

    assertTrue(
        thrown.getMessage().contains("pidinst.b2inst.community.id"),
        "the developer message should name the property, for the logs");
    assertNotNull(thrown.getReason(), "a provider failure must always carry a reason");
    assertFalse(
        thrown.getReason().contains("pidinst.b2inst.community.id"),
        "the reason is shown to a user and must not disclose an internal property name");
  }

  @Test
  void publishDoiSurfacesFieldValidationErrorsFromB2inst() {
    connector.reloadClient();
    MockRestServiceServer server =
        MockRestServiceServer.bindTo(connector.getRestTemplate()).build();
    String validationError =
        "{\"status\":400,\"message\":\"A validation error"
            + " occurred.\",\"errors\":[{\"field\":\"instrument_type\",\"messages\":[\"Missing data"
            + " for required field.\"]},{\"field\":\"owners\",\"messages\":[\"Shorter than minimum"
            + " length 1.\"]}]}";
    expectNoExistingReview(server);
    server
        .expect(requestTo(REVIEW_URL))
        .andExpect(method(HttpMethod.PUT))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(validationError));

    B2instConnectionException ex =
        assertThrows(B2instConnectionException.class, () -> connector.publishDoi("k2j9p-7yh21"));

    assertEquals(
        "Error submitting B2INST record k2j9p-7yh21 for community review: "
            + "instrument_type: Missing data for required field.; "
            + "owners: Shorter than minimum length 1.",
        ex.getMessage());
    server.verify();
  }

  @Test
  void publishDoiSurfacesTopLevelMessageWhenNoFieldErrors() {
    connector.reloadClient();
    MockRestServiceServer server =
        MockRestServiceServer.bindTo(connector.getRestTemplate()).build();
    expectNoExistingReview(server);
    server
        .expect(requestTo(REVIEW_URL))
        .andExpect(method(HttpMethod.PUT))
        .andRespond(
            withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":403,\"message\":\"Permission denied.\"}"));

    B2instConnectionException ex =
        assertThrows(B2instConnectionException.class, () -> connector.publishDoi("k2j9p-7yh21"));

    assertEquals(
        "Error submitting B2INST record k2j9p-7yh21 for community review: Permission denied.",
        ex.getMessage());
    server.verify();
  }

  @Test
  void publishDoiFallsBackToHttpStatusWhenBodyIsNotJson() {
    connector.reloadClient();
    MockRestServiceServer server =
        MockRestServiceServer.bindTo(connector.getRestTemplate()).build();
    expectNoExistingReview(server);
    server
        .expect(requestTo(REVIEW_URL))
        .andExpect(method(HttpMethod.PUT))
        .andRespond(
            withStatus(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.TEXT_HTML)
                .body("<html>Bad Gateway</html>"));

    B2instConnectionException ex =
        assertThrows(B2instConnectionException.class, () -> connector.publishDoi("k2j9p-7yh21"));

    assertEquals(
        "Error submitting B2INST record k2j9p-7yh21 for community review: "
            + "B2INST returned HTTP 502 Bad Gateway",
        ex.getMessage());
    server.verify();
  }

  @Test
  void publishDoiLogsClarifiedAndAbbreviatedWarningWhenNoUsableReason() {
    connector.reloadClient();
    MockRestServiceServer server =
        MockRestServiceServer.bindTo(connector.getRestTemplate()).build();
    String longHtmlBody = "<html>" + "x".repeat(2000) + "</html>";
    expectNoExistingReview(server);
    server
        .expect(requestTo(REVIEW_URL))
        .andExpect(method(HttpMethod.PUT))
        .andRespond(
            withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML).body(longHtmlBody));

    List<String> warnings = new ArrayList<>();
    AbstractAppender capture =
        new AbstractAppender("b2instWarnCapture", null, null, true, Property.EMPTY_ARRAY) {
          @Override
          public void append(LogEvent event) {
            warnings.add(event.getMessage().getFormattedMessage());
          }
        };
    capture.start();
    org.apache.logging.log4j.core.Logger coreLogger =
        (org.apache.logging.log4j.core.Logger) LogManager.getLogger(B2instConnectorImpl.class);
    coreLogger.addAppender(capture);
    try {
      assertThrows(B2instConnectionException.class, () -> connector.publishDoi("k2j9p-7yh21"));
    } finally {
      coreLogger.removeAppender(capture);
      capture.stop();
    }
    server.verify();

    String warning =
        warnings.stream()
            .filter(message -> message.contains("B2INST error response"))
            .findFirst()
            .orElseThrow();
    assertTrue(warning.contains("No usable failure reason"));
    assertTrue(warning.contains("..."));
    assertTrue(warning.length() < 700);
  }

  @Test
  void publishDoiFallsBackToHttpStatusWhenJsonCarriesNothingUsable() {
    connector.reloadClient();
    MockRestServiceServer server =
        MockRestServiceServer.bindTo(connector.getRestTemplate()).build();
    expectNoExistingReview(server);
    server
        .expect(requestTo(REVIEW_URL))
        .andExpect(method(HttpMethod.PUT))
        .andRespond(
            withStatus(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":502}"));

    B2instConnectionException ex =
        assertThrows(B2instConnectionException.class, () -> connector.publishDoi("k2j9p-7yh21"));

    assertEquals(
        "Error submitting B2INST record k2j9p-7yh21 for community review: "
            + "B2INST returned HTTP 502 Bad Gateway",
        ex.getMessage());
    server.verify();
  }

  @Test
  void publishDoiKeepsTransportErrorMessageWhenNoResponse() {
    connector.reloadClient();
    MockRestServiceServer server =
        MockRestServiceServer.bindTo(connector.getRestTemplate()).build();
    expectNoExistingReview(server);
    server
        .expect(requestTo(REVIEW_URL))
        .andExpect(method(HttpMethod.PUT))
        .andRespond(withException(new IOException("connect timed out")));

    B2instConnectionException ex =
        assertThrows(B2instConnectionException.class, () -> connector.publishDoi("k2j9p-7yh21"));

    assertTrue(
        ex.getMessage()
            .startsWith("Error submitting B2INST record k2j9p-7yh21 for community review: "));
    assertTrue(ex.getMessage().contains("connect timed out"));
    server.verify();
  }

  @Test
  void registerDoiSurfacesFieldValidationErrors() {
    connector.reloadClient();
    MockRestServiceServer server =
        MockRestServiceServer.bindTo(connector.getRestTemplate()).build();
    server
        .expect(requestTo("https://b2inst-test.gwdg.de/api/records"))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    "{\"status\":400,\"message\":\"A validation error occurred.\",\"errors\":"
                        + "[{\"field\":\"community\",\"messages\":[\"Missing data for required"
                        + " field.\"]}]}"));

    B2instConnectionException ex =
        assertThrows(
            B2instConnectionException.class, () -> connector.registerDoi(draftWithName("X")));

    assertEquals(
        "Error creating B2INST draft record: community: Missing data for required field.",
        ex.getMessage());
    server.verify();
  }

  @Test
  void publishDoiRedactsConfiguredTokenFromWarnLog() {
    connector.reloadClient();
    MockRestServiceServer server =
        MockRestServiceServer.bindTo(connector.getRestTemplate()).build();
    expectNoExistingReview(server);
    server
        .expect(requestTo(REVIEW_URL))
        .andExpect(method(HttpMethod.PUT))
        .andRespond(
            withStatus(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.TEXT_HTML)
                .body("<html>debug echo: Authorization Bearer TOK123</html>"));

    List<String> warnings = new ArrayList<>();
    AbstractAppender capture =
        new AbstractAppender("b2instRedactCapture", null, null, true, Property.EMPTY_ARRAY) {
          @Override
          public void append(LogEvent event) {
            warnings.add(event.getMessage().getFormattedMessage());
          }
        };
    capture.start();
    org.apache.logging.log4j.core.Logger coreLogger =
        (org.apache.logging.log4j.core.Logger) LogManager.getLogger(B2instConnectorImpl.class);
    coreLogger.addAppender(capture);
    try {
      assertThrows(B2instConnectionException.class, () -> connector.publishDoi("k2j9p-7yh21"));
    } finally {
      coreLogger.removeAppender(capture);
      capture.stop();
    }
    server.verify();

    String warning =
        warnings.stream()
            .filter(message -> message.contains("B2INST error response"))
            .findFirst()
            .orElseThrow();
    assertFalse(warning.contains("TOK123"));
    assertTrue(warning.contains("***"));
  }

  @Test
  void describeFailureFallsBackToExceptionTypeWhenMessageMissing() throws Exception {
    connector.reloadClient();
    Method describeFailure =
        B2instConnectorImpl.class.getDeclaredMethod("describeFailure", RestClientException.class);
    describeFailure.setAccessible(true);

    Object description = describeFailure.invoke(connector, new RestClientException((String) null));

    assertEquals("RestClientException", description);
  }

  @Test
  void retractDoiIsUnsupported() {
    connector.reloadClient();

    assertThrows(UnsupportedOperationException.class, () -> connector.retractDoi("k2j9p-7yh21"));
  }
}
