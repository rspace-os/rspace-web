package com.researchspace.conversion;

import org.springframework.http.HttpStatus;

final class ConversionException extends RuntimeException {

  private final HttpStatus status;
  private final ConversionError error;

  ConversionException(HttpStatus status, ConversionError error, String message) {
    super(message);
    this.status = status;
    this.error = error;
  }

  ConversionException(HttpStatus status, ConversionError error, String message, Throwable cause) {
    super(message, cause);
    this.status = status;
    this.error = error;
  }

  HttpStatus status() {
    return status;
  }

  ConversionError error() {
    return error;
  }
}
