package com.researchspace.api.v2.controller;

import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Renders v2 controller errors as RFC 9457 {@link ApiV2Problem} bodies, overriding the v1 advice.
 */
@RestControllerAdvice(basePackages = "com.researchspace.api.v2.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiV2ControllerAdvice {

  @ExceptionHandler(BindException.class)
  public ResponseEntity<ApiV2Problem> handleBindException(BindException ex) {
    String detail =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> fieldError.getField() + " is invalid")
            .distinct()
            .collect(Collectors.joining("; "));
    return ApiV2Problem.response(HttpStatus.BAD_REQUEST, detail.isEmpty() ? null : detail);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiV2Problem> handleResponseStatus(ResponseStatusException ex) {
    return ApiV2Problem.response(ex.getStatus(), ex.getReason());
  }
}
