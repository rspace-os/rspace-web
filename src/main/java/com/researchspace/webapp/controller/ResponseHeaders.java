package com.researchspace.webapp.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;

/** Shared response headers for endpoints that serve stored file content back to a browser. */
public final class ResponseHeaders {

  private static final String CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
  private static final String NOSNIFF = "nosniff";

  private ResponseHeaders() {}

  /**
   * Tells the browser to trust the declared content type instead of guessing one from the bytes.
   * Uploaded content is only ever as safe as the type it is served under, so a browser that sniffs
   * its own type could treat a file stored as an image as something executable instead.
   */
  public static void preventContentSniffing(HttpServletResponse response) {
    response.setHeader(CONTENT_TYPE_OPTIONS, NOSNIFF);
  }

  /** Header-object equivalent of {@link #preventContentSniffing(HttpServletResponse)}. */
  public static void preventContentSniffing(HttpHeaders headers) {
    headers.set(CONTENT_TYPE_OPTIONS, NOSNIFF);
  }
}
