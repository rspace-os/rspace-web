package com.axiope.webapp.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * inferFallbackContentType is a pure function of the request path, so it lives here rather than
 * alongside the proxying tests, where it would inherit an unrelated HTTP-exchange fixture.
 */
public class ViteDevServerProxyServletMimeTypeTest {

  private final ViteDevServerProxyServlet servlet =
      new ViteDevServerProxyServlet("http://127.0.0.1:5173", null);

  @Test
  public void doesNotInventMimeTypeForUnknownExtensions() {
    assertNull(servlet.inferFallbackContentType("/ui/dist/assets/logo.svg"));
    assertEquals("text/css", servlet.inferFallbackContentType("/ui/dist/assets/app.css"));
    assertEquals(
        "text/javascript", servlet.inferFallbackContentType("/ui/dist/chunks/editor-plugin.mjs"));
  }
}
