package com.researchspace.documentconversion.ext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.researchspace.documentconversion.spi.ConversionResult;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
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
                Duration.ofSeconds(1),
                1024,
                1024));
  }

  @Test
  void disablesCompressedResponses() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://converter.test");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(MockRestRequestMatchers.header(HttpHeaders.ACCEPT_ENCODING, "identity"))
        .andRespond(
            withSuccess(
                "{\"protocol\":\"rspace-conversion-sidecar\",\"version\":1,\"roles\":[\"pdf\",\"word\"]}",
                MediaType.APPLICATION_JSON));

    new ConversionSidecarHttpClient(builder, 1024).requireCapabilities();

    server.verify();
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
  void enforcesWallClockTimeoutAcrossTheWholeRequest() throws Exception {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://converter.test");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(request -> {})
        .andRespond(
            request -> {
              try {
                Thread.sleep(250);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.io.IOException("interrupted test response", e);
              }
              return withSuccess("<html></html>", MediaType.TEXT_HTML).createResponse(request);
            });
    var client =
        new ConversionSidecarHttpClient(builder.build(), 1024, 1024, Duration.ofMillis(25));
    Path input = directory.resolve("timeout-input.doc");
    Files.writeString(input, "input");

    ConversionResult result =
        client.postFile(
            "/v1/convert/html",
            "file",
            input.toFile(),
            directory.resolve("timeout-output.html").toFile(),
            MediaType.TEXT_HTML,
            ignored -> {});

    assertEquals(DocumentConversionError.TIMEOUT.code(), result.getErrorMsg());
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
