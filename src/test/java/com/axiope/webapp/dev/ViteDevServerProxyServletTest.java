package com.axiope.webapp.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ViteDevServerProxyServletTest {

  @Mock private HttpClient client;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private HttpResponse<InputStream> upstreamResponse;

  private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
  private final AtomicReference<String> responseContentType = new AtomicReference<>();
  private ViteDevServerProxyServlet servlet;

  @BeforeEach
  public void setUp() throws Exception {
    servlet = new ViteDevServerProxyServlet("http://127.0.0.1:5173", client);

    when(request.getMethod()).thenReturn("GET");
    when(request.getHeader("Upgrade")).thenReturn(null);
    when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getQueryString()).thenReturn("v=fca605f6");
    when(response.getOutputStream()).thenReturn(new TestServletOutputStream(responseBody));
    when(response.getContentType()).thenAnswer(invocation -> responseContentType.get());

    doAnswer(
            invocation -> {
              responseContentType.set(invocation.getArgument(0, String.class));
              return null;
            })
        .when(response)
        .setContentType(any(String.class));

    when(upstreamResponse.statusCode()).thenReturn(HttpServletResponse.SC_OK);
    when(upstreamResponse.body())
        .thenReturn(
            new ByteArrayInputStream("export const ok = true;".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  public void fallsBackToJavaScriptMimeTypeForJsModuleWhenUpstreamOmitsContentType()
      throws Exception {
    stubSuccessfulUpstreamRequest();
    when(request.getRequestURI())
        .thenReturn(
            "/ui/dist/node_modules/.vite/deps/@fortawesome_free-solid-svg-icons_faThList.js");
    when(upstreamResponse.headers())
        .thenReturn(HttpHeaders.of(Collections.emptyMap(), (a, b) -> true));

    servlet.service(request, response);

    verify(response).setStatus(HttpServletResponse.SC_OK);
    verify(response).setContentType("text/javascript");
    assertEquals("text/javascript", responseContentType.get());
    assertTrue(responseBody.toString(StandardCharsets.UTF_8).contains("export const ok"));
  }

  @Test
  public void overridesTextPlainContentTypeForJsModuleResponses() throws Exception {
    stubSuccessfulUpstreamRequest();
    when(request.getRequestURI())
        .thenReturn("/ui/dist/node_modules/.vite/deps/@mui_icons-material_CheckCircleOutline.js");
    when(upstreamResponse.headers())
        .thenReturn(
            HttpHeaders.of(
                Collections.singletonMap(
                    "content-type", Collections.singletonList("text/plain;charset=utf-8")),
                (a, b) -> true));

    servlet.service(request, response);

    assertEquals("text/javascript", responseContentType.get());
    verify(response).setContentType("text/javascript");
  }

  @Test
  public void forwardsBasePrefixedViteDependencyRequestsToBasePrefixedUpstreamPath()
      throws Exception {
    AtomicReference<URI> upstreamUri = new AtomicReference<>();
    when(request.getRequestURI())
        .thenReturn("/ui/dist/node_modules/.vite/deps/@mui_icons-material_CheckCircleOutline.js");
    when(request.getQueryString()).thenReturn("v=cac0f517");
    when(upstreamResponse.headers())
        .thenReturn(HttpHeaders.of(Collections.emptyMap(), (a, b) -> true));
    when(client.send(
            any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
        .thenAnswer(
            invocation -> {
              upstreamUri.set(invocation.getArgument(0, HttpRequest.class).uri());
              return upstreamResponse;
            });

    servlet.service(request, response);

    assertEquals(
        URI.create(
            "http://127.0.0.1:5173/ui/dist/node_modules/.vite/deps/"
                + "@mui_icons-material_CheckCircleOutline.js?v=cac0f517"),
        upstreamUri.get());
  }

  @Test
  public void preservesUpstreamContentTypeWhenPresent() throws Exception {
    stubSuccessfulUpstreamRequest();
    when(request.getRequestURI()).thenReturn("/ui/dist/src/entries/tinymceGallery.tsx");
    when(upstreamResponse.headers())
        .thenReturn(
            HttpHeaders.of(
                Collections.singletonMap(
                    "content-type", Collections.singletonList("application/javascript")),
                (a, b) -> true));

    servlet.service(request, response);

    assertEquals("application/javascript", responseContentType.get());
    verify(response, never()).setContentType("text/javascript");
  }

  @Test
  public void filtersHopByHopRequestHeadersUsingLocaleIndependentLowercasing() throws Exception {
    Locale originalLocale = Locale.getDefault();
    AtomicReference<HttpRequest> proxiedRequest = new AtomicReference<>();
    try {
      Locale.setDefault(new Locale("tr", "TR"));
      when(request.getRequestURI()).thenReturn("/ui/dist/src/entries/tinymceGallery.tsx");
      when(request.getHeaderNames())
          .thenReturn(Collections.enumeration(List.of("KEEP-ALIVE", "X-Test")));
      when(request.getHeaders("X-Test")).thenReturn(Collections.enumeration(List.of("value")));
      when(upstreamResponse.headers())
          .thenReturn(HttpHeaders.of(Collections.emptyMap(), (a, b) -> true));
      when(client.send(
              any(HttpRequest.class),
              ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
          .thenAnswer(
              invocation -> {
                proxiedRequest.set(invocation.getArgument(0, HttpRequest.class));
                return upstreamResponse;
              });

      servlet.service(request, response);

      assertFalse(proxiedRequest.get().headers().firstValue("KEEP-ALIVE").isPresent());
      assertEquals("value", proxiedRequest.get().headers().firstValue("X-Test").orElse(null));
    } finally {
      Locale.setDefault(originalLocale);
    }
  }

  private static final class TestServletOutputStream extends ServletOutputStream {
    private final ByteArrayOutputStream target;

    private TestServletOutputStream(ByteArrayOutputStream target) {
      this.target = target;
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
      // No-op for unit tests.
    }

    @Override
    public void write(int b) {
      target.write(b);
    }
  }

  private void stubSuccessfulUpstreamRequest() throws Exception {
    when(client.send(
            any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
        .thenReturn(upstreamResponse);
  }
}
