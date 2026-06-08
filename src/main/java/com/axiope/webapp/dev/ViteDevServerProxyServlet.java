package com.axiope.webapp.dev;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reverse-proxies HTTP requests for {@code /ui/dist/*} to a local Vite dev server during local
 * development. Registered programmatically by {@link com.axiope.webapp.listener.StartupListener}
 * only when {@code reactDevMode=true} so it is never present in production deployments.
 *
 * <p>The HMR WebSocket is not proxied; the browser connects directly to the Vite dev server using
 * the {@code hmr.host} / {@code hmr.clientPort} settings in {@code vite.config.ts}.
 *
 * <p><strong>Threading note:</strong> This servlet performs <em>synchronous, blocking</em> HTTP
 * calls to the upstream Vite server — each proxied request occupies a Jetty thread for the full
 * round-trip. This is acceptable for local single-developer use but would not scale to production
 * traffic. Pages that trigger many concurrent module requests (20+ imports) may briefly saturate a
 * small Jetty thread pool; increase {@code jetty.threads.max} if this becomes an issue during
 * development.
 */
public class ViteDevServerProxyServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;
  private static final Logger log = LoggerFactory.getLogger(ViteDevServerProxyServlet.class);
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
  private static final String CONTENT_TYPE_HEADER = "content-type";

  /** Default Vite dev server origin used when {@code -DviteDevServerOrigin} is unset. */
  public static final String DEFAULT_ORIGIN = "http://127.0.0.1:5173";

  // Hop-by-hop headers per RFC 7230 — must not be forwarded by a proxy.
  private static final Set<String> HOP_BY_HOP =
      Set.of(
          "connection",
          "keep-alive",
          "proxy-authenticate",
          "proxy-authorization",
          "te",
          "trailers",
          "transfer-encoding",
          "upgrade",
          "host",
          "content-length");

  private final String upstreamOrigin;
  private final HttpClient client;

  public ViteDevServerProxyServlet(String upstreamOrigin) {
    this(upstreamOrigin, defaultHttpClient());
  }

  ViteDevServerProxyServlet(String upstreamOrigin, HttpClient client) {
    this.upstreamOrigin =
        upstreamOrigin != null && upstreamOrigin.endsWith("/")
            ? upstreamOrigin.substring(0, upstreamOrigin.length() - 1)
            : upstreamOrigin;
    this.client = client;
  }

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    if ("websocket".equalsIgnoreCase(req.getHeader("Upgrade"))) {
      // HMR WS upgrades are served directly by the Vite dev server, not proxied through Jetty.
      resp.sendError(
          HttpServletResponse.SC_NOT_IMPLEMENTED,
          "WebSocket upgrades to the Vite dev server are not proxied; the HMR client should"
              + " connect directly to the dev server (see vite.config.ts hmr settings).");
      return;
    }

    URI target = buildUpstreamUri(req);
    HttpRequest.Builder builder = HttpRequest.newBuilder(target).timeout(REQUEST_TIMEOUT);
    copyRequestHeaders(req, builder);
    builder.method(req.getMethod(), bodyPublisherFor(req));

    HttpResponse<InputStream> upstreamResponse;
    try {
      upstreamResponse = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
    } catch (ConnectException e) {
      log.warn(
          "Cannot reach Vite dev server at {} — is `npm run serve` running? ({})",
          upstreamOrigin,
          e.getMessage());
      resp.sendError(
          HttpServletResponse.SC_BAD_GATEWAY,
          "Vite dev server is not reachable at "
              + upstreamOrigin
              + ". Start it with `npm run serve` in src/main/webapp/ui.");
      return;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      resp.sendError(
          HttpServletResponse.SC_BAD_GATEWAY,
          "Interrupted while proxying to Vite dev server at " + upstreamOrigin);
      return;
    } catch (IOException e) {
      log.warn("Failed to proxy {} to Vite dev server: {}", req.getRequestURI(), e.getMessage());
      throw e;
    }

    resp.setStatus(upstreamResponse.statusCode());
    copyResponseHeaders(upstreamResponse, resp);
    applyFallbackContentType(req, resp);

    try (InputStream upstreamBody = upstreamResponse.body();
        OutputStream clientBody = resp.getOutputStream()) {
      upstreamBody.transferTo(clientBody);
    }
  }

  private static HttpClient defaultHttpClient() {
    return HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .version(HttpClient.Version.HTTP_1_1)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  private HttpRequest.BodyPublisher bodyPublisherFor(HttpServletRequest req) {
    String method = req.getMethod();
    if ("GET".equalsIgnoreCase(method)
        || "HEAD".equalsIgnoreCase(method)
        || "DELETE".equalsIgnoreCase(method)
        || "OPTIONS".equalsIgnoreCase(method)) {
      return HttpRequest.BodyPublishers.noBody();
    }
    return HttpRequest.BodyPublishers.ofInputStream(
        () -> {
          try {
            return req.getInputStream();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  private URI buildUpstreamUri(HttpServletRequest req) {
    String path = req.getRequestURI();
    String query = req.getQueryString();
    return URI.create(upstreamOrigin + path + (query == null ? "" : "?" + query));
  }

  private void copyRequestHeaders(HttpServletRequest req, HttpRequest.Builder builder) {
    req.getHeaderNames()
        .asIterator()
        .forEachRemaining(
            name -> {
              if (HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT))) {
                return;
              }
              req.getHeaders(name)
                  .asIterator()
                  .forEachRemaining(value -> builder.header(name, value));
            });
  }

  private void copyResponseHeaders(HttpResponse<?> upstreamResponse, HttpServletResponse resp) {
    upstreamResponse
        .headers()
        .map()
        .forEach(
            (name, values) -> {
              if (HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT))) {
                return;
              }
              if (CONTENT_TYPE_HEADER.equalsIgnoreCase(name)) {
                values.stream().findFirst().ifPresent(resp::setContentType);
                return;
              }
              for (String value : values) {
                resp.addHeader(name, value);
              }
            });
  }

  /**
   * Applies a fallback Content-Type when the upstream Vite server returns a generic MIME type (e.g.
   * {@code text/plain} or {@code application/octet-stream}) for source files such as {@code .ts},
   * {@code .tsx}, or {@code .jsx}. Without this, the browser may refuse to execute ESM modules that
   * arrive with a non-JavaScript Content-Type.
   */
  void applyFallbackContentType(HttpServletRequest req, HttpServletResponse resp) {
    String fallbackContentType = inferFallbackContentType(req.getRequestURI());
    if (fallbackContentType == null) {
      return;
    }

    String existingContentType = resp.getContentType();
    if (StringUtils.isBlank(existingContentType)
        || shouldOverrideContentType(existingContentType, fallbackContentType)) {
      resp.setContentType(fallbackContentType);
    }
  }

  private boolean shouldOverrideContentType(
      String existingContentType, String fallbackContentType) {
    String contentType = contentTypeEssence(existingContentType);
    if (contentType == null) {
      return false;
    }
    if ("text/javascript".equals(fallbackContentType) || "text/css".equals(fallbackContentType)) {
      return "text/plain".equals(contentType) || "application/octet-stream".equals(contentType);
    }
    return false;
  }

  private String contentTypeEssence(String contentType) {
    String trimmed = StringUtils.trimToNull(contentType);
    if (trimmed == null) {
      return null;
    }
    int parameterStart = trimmed.indexOf(';');
    String essence = parameterStart == -1 ? trimmed : trimmed.substring(0, parameterStart);
    return essence.trim().toLowerCase(Locale.ROOT);
  }

  String inferFallbackContentType(String requestUri) {
    if (StringUtils.isBlank(requestUri)) {
      return null;
    }
    if (requestUri.endsWith(".css")) {
      return "text/css";
    }
    if (requestUri.endsWith(".js")
        || requestUri.endsWith(".mjs")
        || requestUri.endsWith(".ts")
        || requestUri.endsWith(".tsx")
        || requestUri.endsWith(".jsx")) {
      return "text/javascript";
    }
    return null;
  }
}
