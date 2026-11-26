package com.researchspace.webapp.integrations.argos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.argos.model.ArgosDMP;
import com.researchspace.argos.model.ArgosDMPListing;
import com.researchspace.argos.model.DataTableData;
import com.researchspace.service.DMPManager;
import com.researchspace.service.MediaManager;
import com.researchspace.service.UserManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClientException;

/** Runs the provider against a local server that speaks Argos's current public plan API. */
class ArgosDMPProviderApiTest {

  private static final String PLAN_ID = "11111111-1111-4111-8111-111111111111";
  private static final String NO_CONTENT_ID = "no-content";
  private static final String EMPTY_OBJECT_ID = "empty-object";
  private static final String NO_LABEL_ID = "no-label";
  private static final Path FIXTURES = Path.of("src/test/resources/TestResources/argos");

  private record Request(String method, String uri, String tenant, String body) {}

  private final List<Request> requests = new ArrayList<>();
  private HttpServer server;
  private ArgosDMPProvider provider;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/api/plan/public/", this::handle);
    server.start();
    provider =
        new ArgosDMPProvider(new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/api"));
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  private void handle(HttpExchange exchange) throws IOException {
    String body;
    try (InputStream in = exchange.getRequestBody()) {
      body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    String path = exchange.getRequestURI().getPath();
    requests.add(
        new Request(
            exchange.getRequestMethod(),
            exchange.getRequestURI().toString(),
            exchange.getRequestHeaders().getFirst("x-tenant"),
            body));
    String method = exchange.getRequestMethod();
    byte[] response = "{\"error\":\"not found\"}".getBytes(StandardCharsets.UTF_8);
    int status = 404;
    if ("POST".equals(method) && path.equals("/api/plan/public/query")) {
      response = Files.readAllBytes(FIXTURES.resolve("public-plan-query.json"));
      status = 200;
    } else if ("GET".equals(method) && path.equals("/api/plan/public/" + PLAN_ID)) {
      response = Files.readAllBytes(FIXTURES.resolve("public-plan.json"));
      status = 200;
    } else if (path.equals("/api/plan/public/" + EMPTY_OBJECT_ID)) {
      response = "{}".getBytes(StandardCharsets.UTF_8);
      status = 200;
    } else if (path.equals("/api/plan/public/" + NO_LABEL_ID)) {
      response = "{\"id\":\"no-label\"}".getBytes(StandardCharsets.UTF_8);
      status = 200;
    } else if (path.equals("/api/plan/public/" + NO_CONTENT_ID)) {
      exchange.sendResponseHeaders(204, -1);
      exchange.close();
      return;
    }
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, response.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(response);
    }
  }

  @Test
  void listPlansQueriesPublicPlansAndMapsListings() throws Exception {
    DataTableData<ArgosDMPListing> plans = provider.listPlans(10, 2, " soil ");

    assertEquals(1, requests.size());
    Request request = requests.get(0);
    assertEquals("POST", request.method());
    assertEquals("/api/plan/public/query", request.uri());
    assertEquals("default", request.tenant());
    JsonNode query = new ObjectMapper().readTree(request.body());
    assertEquals(20, query.path("page").path("offset").asInt());
    assertEquals(10, query.path("page").path("size").asInt());
    // Argos wraps the term itself; a "%term%" pattern matches nothing.
    assertEquals("soil", query.path("like").asText());
    assertThat(query.path("project").path("fields").toString())
        .contains("planReferences.reference.type.code");

    assertEquals(42, plans.getTotalCount());
    assertThat(plans.getData()).hasSize(3);
    ArgosDMPListing first = plans.getData().get(0);
    assertEquals(PLAN_ID, first.id);
    assertEquals("Example Plan for Soil Samples", first.label);
    assertEquals("Example Grant 101", first.grant);
    assertEquals(Instant.parse("2025-01-02T03:04:05.678Z").toEpochMilli(), first.createdAt);
    assertEquals(Instant.parse("2025-02-03T04:05:06Z").toEpochMilli(), first.modifiedAt);
  }

  @Test
  void listPlansToleratesStringFlagsAndMissingFields() throws Exception {
    List<ArgosDMPListing> plans = provider.listPlans(10, 0, null).getData();

    ArgosDMPListing stringFlags = plans.get(1);
    assertEquals("Example Grant 202", stringFlags.grant);
    assertEquals(0, stringFlags.modifiedAt);
    ArgosDMPListing noReferences = plans.get(2);
    assertNull(noReferences.grant);
    assertEquals(0, noReferences.createdAt);
    assertFalse(new ObjectMapper().readTree(requests.get(0).body()).has("like"));
  }

  @Test
  void getPlanByIdFetchesPublicPlanAndKeepsSavedDmpLayout() throws Exception {
    ArgosDMP plan = provider.getPlanById(PLAN_ID);

    Request request = requests.get(0);
    assertEquals("GET", request.method());
    assertThat(request.uri())
        .startsWith("/api/plan/public/" + PLAN_ID + "?")
        .contains("f=entityDois.doi", "f=planReferences.reference.type.code");
    assertEquals("default", request.tenant());
    assertEquals("Example Plan for Soil Samples", plan.getLabel());
    assertEquals("ref-grant", plan.getGrant().getId());
    assertEquals("Example Grant 101", plan.getGrant().getLabel());

    // Deposits read the DOI back from the saved DMP file.
    String saved = ArgosDMPProvider.OBJECT_MAPPER.writeValueAsString(plan);
    ArgosDMP reread = new ObjectMapper().readValue(saved, ArgosDMP.class);
    assertEquals("Example Plan for Soil Samples", reread.getLabel());
    assertEquals("10.5281/zenodo.0000001", reread.getDoi().orElseThrow());
  }

  @ParameterizedTest
  @ValueSource(strings = {NO_CONTENT_ID, EMPTY_OBJECT_ID, NO_LABEL_ID})
  void importRejectsAPlanWithoutIdOrLabelBeforeSaving(String id) {
    MediaManager mediaManager = mock(MediaManager.class);
    DMPManager dmpManager = mock(DMPManager.class);
    provider.mediaManager = mediaManager;
    provider.dmpManager = dmpManager;
    provider.userManager = mock(UserManager.class);

    assertThrows(RestClientException.class, () -> provider.importDmp(id));
    verifyNoInteractions(mediaManager, dmpManager);
  }
}
