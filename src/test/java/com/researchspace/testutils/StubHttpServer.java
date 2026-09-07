package com.researchspace.testutils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A minimal canned-response HTTP server for tests, built on the JDK's own {@link HttpServer}.
 *
 * <p>It replaces MockServer for the handful of tests that only need "given this method, path and
 * query, return this body". MockServer cost far more than that: two artifacts, twenty-odd
 * exclusions, and a netty version that cannot be upgraded past 5.15.0 without colliding with the
 * AWS SDK.
 *
 * <p>Stubs are matched in registration order. A stub matches when the method and path are equal and
 * every query parameter it declares is present on the request with the declared values. A request
 * matching no stub gets a 404, which is what MockServer did too.
 */
public class StubHttpServer {

  private final HttpServer server;
  private final ExecutorService executor = Executors.newCachedThreadPool();
  private final List<Stub> stubs = new ArrayList<>();

  public StubHttpServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/", this::handle);
    server.setExecutor(executor);
    server.start();
  }

  public int getPort() {
    return server.getAddress().getPort();
  }

  public String getBaseUrl() {
    return "http://localhost:" + getPort();
  }

  /** Drops every registered stub, leaving the server running. */
  public void reset() {
    synchronized (stubs) {
      stubs.clear();
    }
  }

  public void stop() {
    server.stop(0);
    executor.shutdownNow();
  }

  public Stub get(String path) {
    return register("GET", path);
  }

  public Stub post(String path) {
    return register("POST", path);
  }

  private Stub register(String method, String path) {
    Stub stub = new Stub(method, path);
    synchronized (stubs) {
      stubs.add(stub);
    }
    return stub;
  }

  /** A single canned response, matched on method, path and any declared query parameters. */
  public class Stub {
    private final String method;
    private final String path;
    private final Map<String, Set<String>> query = new LinkedHashMap<>();
    private byte[] body = new byte[0];
    private boolean oneShot;

    private Stub(String method, String path) {
      this.method = method;
      this.path = path;
    }

    /** Requires the parameter to be present with all of the given values. */
    public Stub query(String name, String... values) {
      query.computeIfAbsent(name, k -> new LinkedHashSet<>()).addAll(List.of(values));
      return this;
    }

    /** Serves this stub once, after which it stops matching. */
    public Stub once() {
      oneShot = true;
      return this;
    }

    public void respond(String responseBody) {
      respond(responseBody.getBytes(StandardCharsets.UTF_8));
    }

    public void respond(byte[] responseBody) {
      this.body = responseBody;
    }

    private boolean matches(String reqMethod, String reqPath, Map<String, Set<String>> reqQuery) {
      if (!method.equals(reqMethod) || !path.equals(reqPath)) {
        return false;
      }
      return query.entrySet().stream()
          .allMatch(e -> reqQuery.getOrDefault(e.getKey(), Set.of()).containsAll(e.getValue()));
    }
  }

  private void handle(HttpExchange exchange) throws IOException {
    try (InputStream in = exchange.getRequestBody()) {
      in.readAllBytes(); // a POST body must be drained before the response is written
    }
    String path = exchange.getRequestURI().getPath();
    Map<String, Set<String>> query = parseQuery(exchange.getRequestURI().getRawQuery());
    String method = exchange.getRequestMethod();

    byte[] body = null;
    synchronized (stubs) {
      for (Iterator<Stub> iterator = stubs.iterator(); iterator.hasNext(); ) {
        Stub stub = iterator.next();
        if (stub.matches(method, path, query)) {
          if (stub.oneShot) {
            iterator.remove();
          }
          body = stub.body;
          break;
        }
      }
    }

    if (body == null) {
      exchange.sendResponseHeaders(404, -1);
      exchange.close();
      return;
    }
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }

  private static Map<String, Set<String>> parseQuery(String rawQuery) {
    Map<String, Set<String>> parsed = new LinkedHashMap<>();
    if (rawQuery == null || rawQuery.isEmpty()) {
      return parsed;
    }
    for (String pair : rawQuery.split("&")) {
      int eq = pair.indexOf('=');
      String name = decode(eq < 0 ? pair : pair.substring(0, eq));
      String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
      parsed.computeIfAbsent(name, k -> new LinkedHashSet<>()).add(value);
    }
    return parsed;
  }

  private static String decode(String s) {
    return URLDecoder.decode(s, StandardCharsets.UTF_8);
  }
}
