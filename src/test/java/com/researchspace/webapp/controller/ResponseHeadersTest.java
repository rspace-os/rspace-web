package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

class ResponseHeadersTest {

  @Test
  void setsContentTypeAndNosniffOnServletResponse() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    ResponseHeaders.setContentTypeAndPreventSniffing(response, MediaType.IMAGE_PNG_VALUE);

    assertEquals(MediaType.IMAGE_PNG_VALUE, response.getContentType());
    assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
  }

  @Test
  void setsContentTypeAndNosniffOnHttpHeaders() {
    HttpHeaders headers = new HttpHeaders();

    ResponseHeaders.setContentTypeAndPreventSniffing(headers, MediaType.IMAGE_JPEG);

    assertEquals(MediaType.IMAGE_JPEG, headers.getContentType());
    assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
  }

  @ParameterizedTest
  @CsvSource({
    "JPG, image/jpeg",
    "png, image/png",
    "gif, image/gif",
    "bmp, image/bmp",
    "tif, image/tiff",
    "tiff, image/tiff"
  })
  void resolvesEveryValidatedImageExtension(String extension, String expectedContentType) {
    assertEquals(
        MediaType.parseMediaType(expectedContentType),
        ResponseHeaders.getContentTypeForImageExtension(extension));
  }
}
