package com.researchspace.documentconversion.ext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.researchspace.documentconversion.spi.ConversionResult;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  void timeoutAbortsDrippingResponsesAndFreesAllFourWorkers() throws Exception {
    Path input = Files.writeString(directory.resolve("drip-input.doc"), "input");
    ExecutorService callers = Executors.newFixedThreadPool(4);
    try (var slow = new ResponseFixture(true, 4);
        var fast = new ResponseFixture(false, 1)) {
      var slowClient = loopbackClient(slow);
      List<Future<ConversionResult>> results = new ArrayList<>();
      for (int i = 0; i < 4; i++) {
        var output = directory.resolve("slow-" + i + ".html").toFile();
        results.add(
            callers.submit(
                () ->
                    slowClient.postFile(
                        "/convert",
                        "file",
                        input.toFile(),
                        output,
                        MediaType.TEXT_HTML,
                        ignored -> {})));
      }
      assertTrue(
          slow.started.await(3, TimeUnit.SECONDS), "All four workers must be reading bodies");
      for (var result : results) {
        assertEquals(
            DocumentConversionError.TIMEOUT.code(), result.get(5, TimeUnit.SECONDS).getErrorMsg());
      }

      var output = directory.resolve("fast.html").toFile();
      var result =
          loopbackClient(fast)
              .postFile(
                  "/convert", "file", input.toFile(), output, MediaType.TEXT_HTML, ignored -> {});

      assertTrue(result.isSuccessful(), result.getErrorMsg());
      assertEquals("<html>fast</html>", Files.readString(output.toPath()));
    } finally {
      callers.shutdownNow();
      assertTrue(callers.awaitTermination(5, TimeUnit.SECONDS));
    }
  }

  private static ConversionSidecarHttpClient loopbackClient(ResponseFixture fixture) {
    return new ConversionSidecarHttpClient(
        "http://127.0.0.1:" + fixture.server.getLocalPort(),
        Duration.ofSeconds(1),
        Duration.ofSeconds(1),
        Duration.ofMillis(1500),
        Duration.ofSeconds(30),
        1024,
        1024 * 1024);
  }

  private static class ResponseFixture implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ResponseFixture.class);
    final ServerSocket server;
    final CountDownLatch started;
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final List<Socket> sockets = new CopyOnWriteArrayList<>();

    ResponseFixture(boolean drip, int requests) throws IOException {
      server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
      started = new CountDownLatch(requests);
      workers.submit(
          () -> {
            try {
              for (int i = 0; i < requests; i++) {
                Socket socket = server.accept();
                sockets.add(socket);
                workers.submit(() -> respond(socket, drip));
              }
            } catch (IOException e) {
              LOG.warn("Loopback response fixture stopped accepting requests", e);
            }
          });
    }

    private void respond(Socket socket, boolean drip) {
      try (socket;
          var reader =
              new BufferedReader(
                  new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {
        socket.setSoTimeout(5000);
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {}
        var output = socket.getOutputStream();
        String body = "<html>fast</html>";
        String headers =
            "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: "
                + (drip ? 1_000_000 : body.length())
                + "\r\nConnection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.US_ASCII));
        output.flush();
        started.countDown();
        if (drip) {
          for (int i = 0; i < 200; i++) {
            output.write('x');
            output.flush();
            Thread.sleep(50);
          }
        } else {
          output.write(body.getBytes(StandardCharsets.US_ASCII));
          output.flush();
        }
      } catch (IOException e) {
        LOG.warn("Loopback response fixture connection closed", e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LOG.warn("Loopback response fixture interrupted", e);
      }
    }

    @Override
    public void close() throws Exception {
      server.close();
      for (Socket socket : sockets) {
        socket.close();
      }
      workers.shutdownNow();
      assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
    }
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
