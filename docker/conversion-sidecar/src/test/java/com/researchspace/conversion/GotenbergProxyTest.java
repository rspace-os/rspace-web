package com.researchspace.conversion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class GotenbergProxyTest {
  @TempDir Path directory;

  @Test
  void abortsContinuouslyArrivingPdfAtWallClockDeadlineAndRemovesPartialOutput() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/forms/libreoffice/convert",
        exchange -> {
          try (exchange) {
            exchange.getRequestBody().transferTo(java.io.OutputStream.nullOutputStream());
            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, 0);
            for (int i = 0; i < 500; i++) {
              exchange
                  .getResponseBody()
                  .write("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
              exchange.getResponseBody().flush();
              try {
                Thread.sleep(10);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
              }
            }
          } catch (java.io.IOException expectedDisconnect) {
            // The client must abort the still-streaming response when its deadline expires.
            org.slf4j.LoggerFactory.getLogger(getClass())
                .warn("Test stream disconnected", expectedDisconnect);
          }
        });
    server.start();
    try {
      var properties =
          new ConverterProperties(
              directory,
              directory,
              Duration.ofMillis(500),
              Duration.ofSeconds(1),
              Duration.ofSeconds(1),
              Duration.ofSeconds(10),
              1,
              100000,
              directory);
      var proxy = spy(new GotenbergProxy(properties));
      doAnswer(
              invocation ->
                  RestClient.builder()
                      .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                      .requestFactory(
                          new HttpComponentsClientHttpRequestFactory(
                              invocation.<CloseableHttpClient>getArgument(0)))
                      .build())
          .when(proxy)
          .client(any());
      Path input = Files.writeString(directory.resolve("uploaded.txt"), "hello");
      assertTimeoutPreemptively(
          Duration.ofSeconds(3),
          () ->
              assertEquals(
                  HttpStatus.GATEWAY_TIMEOUT,
                  assertThrows(ConversionException.class, () -> proxy.convert(input, "txt", "test"))
                      .status()));
      try (var files = Files.list(directory)) {
        assertEquals(0, files.count());
      }
    } finally {
      server.stop(0);
    }
  }
}
