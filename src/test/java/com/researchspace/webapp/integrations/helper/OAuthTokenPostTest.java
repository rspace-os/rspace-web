package com.researchspace.webapp.integrations.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OAuthTokenPostTest {
  @Test
  void credentialsGoInTheBodyAndNeverAppearInFailureUrls() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicReference<String> body = new AtomicReference<>();
    AtomicReference<String> query = new AtomicReference<>();
    server.createContext(
        "/token",
        exchange -> {
          query.set(exchange.getRequestURI().getRawQuery());
          try (var input = exchange.getRequestBody()) {
            body.set(new String(input.readAllBytes(), StandardCharsets.UTF_8));
          }
          exchange.sendResponseHeaders(200, 2);
          try (var output = exchange.getResponseBody()) {
            output.write("{}".getBytes(StandardCharsets.UTF_8));
          }
        });
    server.createContext(
        "/failure",
        exchange -> {
          exchange.sendResponseHeaders(400, -1);
          exchange.close();
        });
    server.start();
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    try {
      assertEquals(
          "{}",
          OAuthTokenPost.exchange(
              base + "/token?grant_type=authorization_code&code=sensitive%2Bcode",
              "client",
              "secret"));
      assertNull(query.get());
      assertEquals("grant_type=authorization_code&code=sensitive%2Bcode", body.get());
      IOException failure =
          assertThrows(
              IOException.class,
              () ->
                  OAuthTokenPost.exchange(
                      base + "/failure?refresh_token=sensitive-token", "client", "secret"));
      assertThat(failure.getMessage()).doesNotContain("sensitive-token", "secret");
    } finally {
      server.stop(0);
    }
  }
}
