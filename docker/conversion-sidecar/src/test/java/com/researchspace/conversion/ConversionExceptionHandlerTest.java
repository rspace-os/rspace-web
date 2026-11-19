package com.researchspace.conversion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

class ConversionExceptionHandlerTest {

  @Test
  void busyResponseIncludesRetryAfter() {
    var error =
        new ConversionException(
            HttpStatus.TOO_MANY_REQUESTS, ConversionError.SERVICE_BUSY, "internal diagnostic");

    var response = new ConversionExceptionHandler().handle(error, new MockHttpServletResponse());

    assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
    assertEquals("1", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    assertEquals(
        ConversionError.SERVICE_BUSY.code(),
        response.getHeaders().getFirst(ConversionError.HEADER));
    assertEquals(ConversionError.SERVICE_BUSY.code(), response.getBody().getTitle());
    assertEquals(ConversionError.SERVICE_BUSY.code(), response.getBody().getDetail());
    assertEquals(
        ConversionError.SERVICE_BUSY.code(), response.getBody().getProperties().get("code"));
    assertDoesNotThrow(
        () -> UUID.fromString(response.getBody().getProperties().get("requestId").toString()));
  }

  @Test
  void unexpectedResponseIncludesRequestId() throws Exception {
    var response =
        new ConversionExceptionHandler()
            .handleUnexpected(new RuntimeException("boom"), new MockHttpServletResponse());

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertDoesNotThrow(
        () -> UUID.fromString(response.getBody().getProperties().get("requestId").toString()));
  }

  @Test
  void uncommittedErrorDropsTheConvertedFileLength() throws Exception {
    var servletResponse = new MockHttpServletResponse();
    servletResponse.setContentLengthLong(36_783_113);
    var error =
        new ConversionException(HttpStatus.GATEWAY_TIMEOUT, ConversionError.TIMEOUT, "deadline");

    new ConversionExceptionHandler().handle(error, servletResponse);

    // Tomcat omits the Content-Length header when the length is -1.
    assertEquals(-1L, servletResponse.getContentLengthLong());
  }

  @Test
  void committedResponseIsLeftUnresolvedSoTheConnectionCloses() throws Exception {
    var servletResponse = new MockHttpServletResponse();
    servletResponse.setCommitted(true);
    var error =
        new ConversionException(HttpStatus.GATEWAY_TIMEOUT, ConversionError.TIMEOUT, "deadline");
    var unexpected = new IllegalStateException("stream failed");
    var handler = new ConversionExceptionHandler();

    assertSame(
        error,
        assertThrows(ConversionException.class, () -> handler.handle(error, servletResponse)));
    assertSame(
        unexpected,
        assertThrows(
            IllegalStateException.class,
            () -> handler.handleUnexpected(unexpected, servletResponse)));
  }
}
