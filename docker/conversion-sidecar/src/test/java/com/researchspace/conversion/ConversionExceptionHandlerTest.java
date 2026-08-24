package com.researchspace.conversion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class ConversionExceptionHandlerTest {

  @Test
  void busyResponseIncludesRetryAfter() {
    var error =
        new ConversionException(
            HttpStatus.TOO_MANY_REQUESTS, ConversionError.SERVICE_BUSY, "internal diagnostic");

    var response = new ConversionExceptionHandler().handle(error);

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
        () ->
            java.util.UUID.fromString(
                response.getBody().getProperties().get("requestId").toString()));
  }

  @Test
  void unexpectedResponseIncludesRequestId() {
    var response = new ConversionExceptionHandler().handleUnexpected(new RuntimeException("boom"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertDoesNotThrow(
        () ->
            java.util.UUID.fromString(
                response.getBody().getProperties().get("requestId").toString()));
  }
}
