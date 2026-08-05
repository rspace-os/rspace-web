package com.researchspace.webapp.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Shared HTTP response headers for controllers that serve stored file content to a browser. */
public final class ResponseHeaders {

  private static final String CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
  private static final String NOSNIFF = "nosniff";

  private ResponseHeaders() {}

  /**
   * Tells the browser to trust the declared content type instead of guessing one from the bytes.
   * Uploaded content is only ever as safe as the type it is served under, so a browser that sniffs
   * its own type could treat a file stored as an image as something executable instead.
   */
  public static void setContentTypeAndPreventSniffing(
      HttpServletResponse response, String contentType) {
    response.setContentType(contentType);
    response.setHeader(CONTENT_TYPE_OPTIONS, NOSNIFF);
  }

  /** Header-object equivalent of {@link #setContentTypeAndPreventSniffing}. */
  public static void setContentTypeAndPreventSniffing(HttpHeaders headers, MediaType contentType) {
    headers.setContentType(contentType);
    headers.set(CONTENT_TYPE_OPTIONS, NOSNIFF);
  }

  /** Resolves every image type accepted by the media upload validator. */
  public static MediaType getContentTypeForImageExtension(String extension) {
    if (extension == null) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
    return switch (extension.toLowerCase(Locale.ROOT)) {
      case "jpeg", "jpg" -> MediaType.IMAGE_JPEG;
      case "gif" -> MediaType.IMAGE_GIF;
      case "png" -> MediaType.IMAGE_PNG;
      case "bmp" -> MediaType.parseMediaType("image/bmp");
      case "tif", "tiff" -> MediaType.parseMediaType("image/tiff");
      default -> MediaType.APPLICATION_OCTET_STREAM;
    };
  }
}
