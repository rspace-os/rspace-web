package com.researchspace.documentconversion.ext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
import org.springframework.mock.env.MockEnvironment;
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
                1024,
                1024,
                "test-token"));
  }

  @Test
  void sendsBearerAuthentication() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://converter.test");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(MockRestRequestMatchers.header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
        .andRespond(
            withSuccess(
                "{\"protocol\":\"rspace-conversion-sidecar\",\"version\":1,\"roles\":[\"pdf\",\"word\"]}",
                MediaType.APPLICATION_JSON));

    new ConversionSidecarHttpClient(builder, "test-token", 1024).requireCapabilities();

    server.verify();
  }

  @Test
  void readsBearerTokenFromConfiguredFile() throws Exception {
    String token = "0123456789abcdef0123456789abcdef";
    Path tokenFile = directory.resolve("conversion-token");
    Files.writeString(tokenFile, token + "\n");
    var environment =
        new MockEnvironment().withProperty("conversion.bearerTokenFile", tokenFile.toString());

    assertEquals(token, ConversionSidecarClientFactory.readBearerToken(environment));
  }

  @Test
  void rejectsShortBearerToken() throws Exception {
    Path tokenFile = directory.resolve("conversion-token");
    Files.writeString(tokenFile, "too-short");
    var environment =
        new MockEnvironment().withProperty("conversion.bearerTokenFile", tokenFile.toString());

    assertThrows(
        IllegalStateException.class,
        () -> ConversionSidecarClientFactory.readBearerToken(environment));
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
