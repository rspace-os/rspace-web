package com.researchspace.documentconversion.ext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sun.net.httpserver.HttpServer;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ConversionSidecarHttpClientTest {

  @TempDir Path directory;

  @Test
  void acceptsAnyHttpOriginAtTheApplicationBoundary() {
    assertDoesNotThrow(
        () ->
            new ConversionSidecarHttpClient(
                "http://converter.any-private-origin.test:8123",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                1024,
                1024));
  }

  @Test
  void sendsValidRequestWithoutAuthorization() throws Exception {
    var authorization = new AtomicReference<String>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/capabilities",
        exchange -> {
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          byte[] response =
              "{\"protocol\":\"rspace-conversion-sidecar\",\"version\":1,\"roles\":[\"pdf\",\"word\"]}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
          exchange.sendResponseHeaders(200, response.length);
          try (var body = exchange.getResponseBody()) {
            body.write(response);
          }
        });
    server.start();

    try {
      new ConversionSidecarHttpClient(
              "http://127.0.0.1:" + server.getAddress().getPort(),
              Duration.ofSeconds(1),
              Duration.ofSeconds(1),
              Duration.ofSeconds(1),
              1024,
              1024)
          .requireCapabilities();
    } finally {
      server.stop(0);
    }

    assertNull(authorization.get());
  }

  @Test
  void rejectsWrongProtocolVersion() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://converter.test");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(request -> {})
        .andRespond(
            withSuccess(
                "{\"protocol\":\"rspace-conversion-sidecar\",\"version\":2,\"roles\":[\"pdf\",\"word\"]}",
                MediaType.APPLICATION_JSON));
    var client = new ConversionSidecarHttpClient(builder.build(), 1024);

    assertThrows(IllegalStateException.class, client::requireCapabilities);
    server.verify();
  }

  @Test
  void returnsAllowListedLogicalErrorFromSidecar() throws Exception {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://converter.test");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(request -> {})
        .andRespond(
            withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .header("X-RSpace-Conversion-Error", DocumentConversionError.OUTPUT_INVALID.code())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("{\"detail\":\"internal text must be ignored\"}"));
    var client = new ConversionSidecarHttpClient(builder.build(), 1024);
    Path input = directory.resolve("input.docx");
    Files.writeString(input, "input");

    var result =
        client.postFile(
            "/v1/convert/html",
            "file",
            input.toFile(),
            directory.resolve("output.html").toFile(),
            MediaType.TEXT_HTML,
            ignored -> {});

    assertEquals(DocumentConversionError.OUTPUT_INVALID.code(), result.getErrorMsg());
    server.verify();
  }

  @Test
  void ignoresUnknownSidecarErrorCode() throws Exception {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://converter.test");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(request -> {})
        .andRespond(
            withStatus(HttpStatus.BAD_GATEWAY)
                .header("X-RSpace-Conversion-Error", "untrusted.internal.message"));
    var client = new ConversionSidecarHttpClient(builder.build(), 1024);
    Path input = directory.resolve("input.docx");
    Files.writeString(input, "input");

    var result =
        client.postFile(
            "/v1/convert/html",
            "file",
            input.toFile(),
            directory.resolve("output.html").toFile(),
            MediaType.TEXT_HTML,
            ignored -> {});

    assertEquals(DocumentConversionError.SERVICE_UNAVAILABLE.code(), result.getErrorMsg());
    server.verify();
  }

  @Test
  void classifiesTimeoutsByCause() {
    var exception = new IllegalStateException("request failed", new SocketTimeoutException());

    assertEquals(
        DocumentConversionError.TIMEOUT, ConversionSidecarHttpClient.errorForException(exception));
  }

  @Test
  void classifiesUnavailableServiceByCause() {
    var exception = new IllegalStateException("request failed", new ConnectException());

    assertEquals(
        DocumentConversionError.SERVICE_UNAVAILABLE,
        ConversionSidecarHttpClient.errorForException(exception));
  }
}
