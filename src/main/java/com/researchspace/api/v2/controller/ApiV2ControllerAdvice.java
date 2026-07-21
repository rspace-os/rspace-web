package com.researchspace.api.v2.controller;

import com.researchspace.api.v1.auth.ApiAuthenticationException;
import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.core.util.throttling.ThrottlingException;
import com.researchspace.service.MessageSourceUtils;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

/**
 * Renders v2 controller errors as RFC 9457 {@link ApiV2Problem} bodies, overriding the v1 advice.
 */
@ControllerAdvice(basePackageClasses = ApiV2ControllerAdvice.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ApiV2ControllerAdvice {

  private final MessageSourceUtils messages;

  public ApiV2ControllerAdvice(MessageSourceUtils messages) {
    this.messages = messages;
  }

  @ExceptionHandler(BindException.class)
  public ResponseEntity<ApiV2Problem> handleBindException(BindException ex) {
    String detail =
        Stream.concat(
                ex.getBindingResult().getFieldErrors().stream(),
                ex.getBindingResult().getGlobalErrors().stream())
            .map(ObjectError.class::cast)
            .map(messages::getMessage)
            .distinct()
            .collect(Collectors.joining("; "));
    return ApiV2Problem.response(HttpStatus.BAD_REQUEST, detail.isEmpty() ? null : detail);
  }

  @ExceptionHandler(TypeMismatchException.class)
  public ResponseEntity<ApiV2Problem> handleTypeMismatch() {
    return problem(HttpStatus.BAD_REQUEST, "errors.api.v2.invalidRequest");
  }

  @ExceptionHandler(ApiAuthenticationException.class)
  public ResponseEntity<ApiV2Problem> handleAuthentication() {
    return problem(HttpStatus.UNAUTHORIZED, "errors.api.v2.authenticationRequired");
  }

  @ExceptionHandler(AuthorizationException.class)
  public ResponseEntity<ApiV2Problem> handleAuthorization() {
    return problem(HttpStatus.FORBIDDEN, "errors.api.v2.forbidden");
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiV2Problem> handleNotFound() {
    return problem(HttpStatus.NOT_FOUND, "errors.api.v2.notFound");
  }

  @ExceptionHandler(ThrottlingException.class)
  public ResponseEntity<ApiV2Problem> handleThrottling() {
    return problem(HttpStatus.TOO_MANY_REQUESTS, "errors.api.v2.tooManyRequests");
  }

  @ExceptionHandler(ApiRuntimeException.class)
  public ResponseEntity<ApiV2Problem> handleApiRuntime(ApiRuntimeException ex) {
    return ApiV2Problem.response(
        HttpStatus.UNPROCESSABLE_ENTITY, messages.getMessage(ex.getErrorCode(), ex.getArgs()));
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiV2Problem> handleResponseStatus(ResponseStatusException ex) {
    return ApiV2Problem.response(ex.getStatus(), ex.getReason());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiV2Problem> handleUnexpected(Exception ex) {
    log.error("Unexpected REST API v2 error", ex);
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, "errors.api.v2.unexpected");
  }

  private ResponseEntity<ApiV2Problem> problem(HttpStatus status, String messageKey) {
    return ApiV2Problem.response(status, messages.getMessage(messageKey));
  }
}
