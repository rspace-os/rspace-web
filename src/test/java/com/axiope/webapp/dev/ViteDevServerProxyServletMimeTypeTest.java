package com.axiope.webapp.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Separate from the proxying tests: inferFallbackContentType needs no mocks. */
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
