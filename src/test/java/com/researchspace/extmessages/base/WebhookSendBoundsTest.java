package com.researchspace.extmessages.base;

import static com.researchspace.testutils.SystemPropertyTestFactory.createAnyAppWithConfigElements;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.researchspace.model.User;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.testutils.TestFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/** The real transport against local sockets: every send ends in bounded time (RSDEV-1340). */
class WebhookSendBoundsTest {

  private static final Duration BOUND = Duration.ofSeconds(5);

  private final List<RawServer> servers = new CopyOnWriteArrayList<>();
  private DummyExternalMessageSender webhookSender;
  private User user;
  private AppConfigElementSet config;

  @BeforeEach
  void setUp() {
    webhookSender = new DummyExternalMessageSender();
    webhookSender.setTimeouts(Duration.ofMillis(200), Duration.ofMillis(500));
    user = TestFactory.createAnyUser("sender");
    config =
        createAnyAppWithConfigElements(user, "message").getAppConfigElementSets().iterator().next();
  }

  @AfterEach
  void tearDown() {
    servers.forEach(RawServer::close);
  }

  @Test
  void endpointThatNeverAnswersTimesOut() throws IOException {
    sendTo(start(out -> sleepUntilClosed()));

    assertTimeoutPreemptively(BOUND, () -> assertThrows(ResourceAccessException.class, this::send));
  }

  @Test
  void endlessSuccessBodyIsAbandoned() throws IOException {
    sendTo(start(out -> endlessBody(out, "200 OK")));

    ResponseEntity<String> response = assertTimeoutPreemptively(BOUND, this::send);

    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  @Test
  void endlessErrorBodyIsAbandoned() throws IOException {
    sendTo(start(out -> endlessBody(out, "500 Internal Server Error")));

    assertTimeoutPreemptively(
        BOUND, () -> assertThrows(HttpServerErrorException.class, this::send));
  }

  @Test
  void redirectIsNotFollowed() throws IOException {
    RawServer target = start(out -> respond(out, "200 OK", ""));
    String location = "Location: http://127.0.0.1:" + target.port() + "/elsewhere\r\n";
    sendTo(start(out -> respond(out, "307 Temporary Redirect", location)));

    ResponseEntity<String> response = assertTimeoutPreemptively(BOUND, this::send);

    assertFalse(response.getStatusCode().is2xxSuccessful());
    assertEquals(0, target.connections());
  }

  private ResponseEntity<String> send() {
    return webhookSender.sendMessage(null, config, user);
  }

  private void sendTo(RawServer server) {
    String origin = "http://127.0.0.1:" + server.port();
    webhookSender.policy = WebhookDestinationPolicy.parse("test.allowed.origins", origin);
    webhookSender.url = origin + "/hook";
  }

  private RawServer start(Handler handler) throws IOException {
    RawServer server = new RawServer(handler);
    servers.add(server);
    return server;
  }

  private static void respond(OutputStream out, String status, String headers) throws IOException {
    out.write(
        ("HTTP/1.1 " + status + "\r\n" + headers + "Content-Length: 0\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII));
    out.flush();
  }

  private static void endlessBody(OutputStream out, String status) throws IOException {
    out.write(
        ("HTTP/1.1 "
                + status
                + "\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII));
    byte[] chunk = ("2000\r\n" + "x".repeat(0x2000) + "\r\n").getBytes(StandardCharsets.US_ASCII);
    while (true) { // until the client goes away
      out.write(chunk);
      out.flush();
    }
  }

  private static void sleepUntilClosed() {
    try {
      Thread.sleep(BOUND.multipliedBy(2).toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @FunctionalInterface
  private interface Handler {
    void handle(OutputStream out) throws IOException;
  }

  /** A minimal HTTP/1.1 server: reads each request's head and body, then runs the handler. */
  private static final class RawServer {
    private final ServerSocket socket;
    private final AtomicInteger connections = new AtomicInteger();

    RawServer(Handler handler) throws IOException {
      socket = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
      Thread acceptor =
          new Thread(
              () -> {
                while (!socket.isClosed()) {
                  try {
                    Socket client = socket.accept();
                    connections.incrementAndGet();
                    Thread worker = new Thread(() -> serve(client, handler));
                    worker.setDaemon(true);
                    worker.start();
                  } catch (IOException e) {
                    return; // closed
                  }
                }
              });
      acceptor.setDaemon(true);
      acceptor.start();
    }

    private static void serve(Socket client, Handler handler) {
      try (client) {
        readRequest(client.getInputStream());
        handler.handle(client.getOutputStream());
      } catch (IOException e) {
        // the client closed the connection, which is what the bounded sends do
      }
    }

    private static void readRequest(InputStream in) throws IOException {
      StringBuilder head = new StringBuilder();
      int b;
      while ((b = in.read()) != -1) {
        head.append((char) b);
        if (head.toString().endsWith("\r\n\r\n")) {
          break;
        }
      }
      for (String line : head.toString().split("\r\n")) {
        if (line.toLowerCase().startsWith("content-length:")) {
          in.readNBytes(Integer.parseInt(line.substring("content-length:".length()).trim()));
        }
      }
    }

    int port() {
      return socket.getLocalPort();
    }

    int connections() {
      return connections.get();
    }

    void close() {
      try {
        socket.close();
      } catch (IOException e) {
        // already closed
      }
    }
  }
}
