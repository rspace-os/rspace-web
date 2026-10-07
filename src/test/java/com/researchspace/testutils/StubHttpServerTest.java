package com.researchspace.testutils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Covers the matching rules {@link StubHttpServer} relies on. */
class StubHttpServerTest {

  private StubHttpServer server;
  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeEach
  void setUp() throws Exception {
    server = new StubHttpServer();
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  private HttpResponse<String> get(String pathAndQuery) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create(server.getBaseUrl() + pathAndQuery)).build(),
        BodyHandlers.ofString());
  }

  @Test
  void unmatchedRequestGets404() throws Exception {
    assertEquals(404, get("/nothing").statusCode());
  }

  @Test
  void matchesOnPath() throws Exception {
    server.get("/api").respond("version");
    HttpResponse<String> response = get("/api");
    assertEquals(200, response.statusCode());
    assertEquals("version", response.body());
  }

  @Test
  void pathMatchIsExact() throws Exception {
    server.get("/api").respond("version");
    assertEquals(404, get("/api/v0").statusCode());
  }

  @Test
  void methodMustMatch() throws Exception {
    server.post("/login").respond("posted");
    assertEquals(404, get("/login").statusCode());
  }

  @Test
  void declaredQueryParametersMustBePresent() throws Exception {
    server.get("/items").query("offset", "0").respond("first page");
    assertEquals("first page", get("/items?offset=0").body());
    assertEquals(404, get("/items?offset=200").statusCode());
  }

  @Test
  void undeclaredQueryParametersAreIgnored() throws Exception {
    server.get("/items").query("offset", "0").respond("first page");
    assertEquals("first page", get("/items?offset=0&childCount=true").body());
  }

  @Test
  void stubsOnOnePathAreDiscriminatedByQuery() throws Exception {
    server.get("/items").query("offset", "0").respond("page one");
    server.get("/items").query("offset", "200").respond("page two");
    assertEquals("page one", get("/items?offset=0").body());
    assertEquals("page two", get("/items?offset=200").body());
  }

  @Test
  void multiValuedQueryParameterRequiresEveryValue() throws Exception {
    server.get("/thumbnails").query("id", "1", "2").respond("both");
    assertEquals("both", get("/thumbnails?id=1&id=2").body());
    assertEquals(404, get("/thumbnails?id=1").statusCode());
  }

  @Test
  void onceStubStopsMatchingAfterOneRequest() throws Exception {
    server.get("/api").once().respond("version");
    assertEquals(200, get("/api").statusCode());
    assertEquals(404, get("/api").statusCode());
  }

  @Test
  void resetDropsEveryStub() throws Exception {
    server.get("/api").respond("version");
    server.reset();
    assertEquals(404, get("/api").statusCode());
  }
}
