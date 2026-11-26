package com.researchspace.documentconversion.ext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;

class ConversionSidecarClientFactoryTest {

  private static final ConversionSidecarClientFactory.Settings SETTINGS =
      new ConversionSidecarClientFactory.Settings(500, 500, 1000, 1000, 1024, 1024, 1024);

  @Test
  void createsClientsWhenTheSidecarIsUnreachableAtStartup() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }

    var clients = ConversionSidecarClientFactory.create("http://127.0.0.1:" + closedPort, SETTINGS);

    assertNotNull(clients.pdf());
    assertNotNull(clients.word());
  }

  @Test
  void failsStartupWhenTheUrlNamesAServiceWithoutTheCapabilitiesRoute() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          try (exchange) {
            exchange.sendResponseHeaders(404, -1);
          }
        });
    server.start();
    try {
      String url = "http://127.0.0.1:" + server.getAddress().getPort();

      assertThrows(
          HttpClientErrorException.class,
          () -> ConversionSidecarClientFactory.create(url, SETTINGS));
    } finally {
      server.stop(0);
    }
  }
}
