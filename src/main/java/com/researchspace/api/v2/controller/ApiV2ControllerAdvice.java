package com.researchspace.api.v2.controller;

import com.ibm.icu.text.ListFormatter;
import com.researchspace.api.v2.auth.ApiV2AuthenticationException;
import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.resource.ApiV2ResourceException;
import com.researchspace.booking.service.BookingPolicyException;
import com.researchspace.booking.service.InvalidBookingSchedulingSettingsException;
import com.researchspace.booking.service.StaleBookingSettingsException;
import com.researchspace.core.util.throttling.ThrottlingException;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.DocumentValidationException;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.service.CollectionMutationException;
import com.researchspace.service.ListFormatUtils;
import com.researchspace.service.MessageSourceUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

/**
 * Renders v2 controller errors as RFC 9457 {@link ApiV2Problem} bodies.
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} makes this advice's {@code Exception.class} handler run
 * before Spring's {@code DefaultHandlerExceptionResolver}, so an exception that Spring would
 * otherwise map to a specific status reaches the catch-all here instead. Rather than enumerate
 * Spring's whole exception list, {@link #handleUnexpected} honours {@link ErrorResponse}, the
 * Spring 6 interface every standard MVC exception implements to carry its own status and headers.
 * That covers types with no handler of their own -- {@code NoResourceFoundException} and {@code
 * NoHandlerFoundException} (404), {@code AsyncRequestTimeoutException} (503) -- and any type a
 * future Spring version adds, which would otherwise silently become a 500.
 */
@ControllerAdvice(basePackageClasses = ApiV2ControllerAdvice.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ApiV2ControllerAdvice {

  private static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  private final MessageSourceUtils messages;

  public ApiV2ControllerAdvice(MessageSourceUtils messages) {
    this.messages = messages;
  }

  @ExceptionHandler(BindException.class)
  public ResponseEntity<ApiV2Problem> handleBindException(BindException ex) {
    String code = "errors.api.v2.invalidRequest";
    List<String> details =
        Stream.concat(
                ex.getBindingResult().getFieldErrors().stream(),
                ex.getBindingResult().getGlobalErrors().stream())
            .map(ObjectError.class::cast)
            .map(this::validationMessage)
            .distinct()
            .toList();
    String detail =
        details.isEmpty() ? null : ListFormatUtils.formatList(details, ListFormatter.Type.UNITS);
    return ApiV2Problem.response(HttpStatus.BAD_REQUEST, messages.getMessage(code), code, detail);
  }

  private String validationMessage(ObjectError error) {
    String interpolatedMessage = error.getDefaultMessage();
    return interpolatedMessage != null ? interpolatedMessage : messages.getMessage(error);
  }

  @ExceptionHandler(TypeMismatchException.class)
  public ResponseEntity<ApiV2Problem> handleTypeMismatch() {
    return problem(HttpStatus.BAD_REQUEST, "errors.api.v2.invalidRequest");
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiV2Problem> handleUnreadableBody() {
    return problem(HttpStatus.BAD_REQUEST, "errors.api.v2.invalidRequest");
  }

  @ExceptionHandler(ApiV2AuthenticationException.class)
  public ResponseEntity<ApiV2Problem> handleAuthentication() {
    String code = "errors.api.v2.authenticationRequired";
    String detail = messages.getMessage(code);
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .contentType(ApiV2Problem.PROBLEM_JSON)
        .body(new ApiV2Problem(detail, HttpStatus.UNAUTHORIZED.value(), code, detail, null));
  }

  @ExceptionHandler(AuthorizationException.class)
  public ResponseEntity<ApiV2Problem> handleAuthorization(
      AuthorizationException exception, HttpServletRequest request) {
    SECURITY_LOG.warn(
        "REST API v2 authorization failure by user [{}] to [{}]: {}",
        principal(request),
        request.getRequestURI(),
        exception.getMessage());
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

  @ExceptionHandler(ApiV2BadRequestException.class)
  public ResponseEntity<ApiV2Problem> handleBadRequest(ApiV2BadRequestException ex) {
    String detail = messages.getMessage(ex.getErrorCode(), ex.getArgs());
    return ApiV2Problem.response(HttpStatus.BAD_REQUEST, detail, ex.getErrorCode(), detail);
  }

  @ExceptionHandler(InvalidBookingSchedulingSettingsException.class)
  public ResponseEntity<ApiV2Problem> handleInvalidBookingSettings(
      InvalidBookingSchedulingSettingsException ex) {
    return problem(HttpStatus.BAD_REQUEST, ex.reason().errorCode());
  }

  @ExceptionHandler(BookingPolicyException.class)
  public ResponseEntity<ApiV2Problem> handleBookingPolicy(BookingPolicyException ex) {
    return problem(HttpStatus.BAD_REQUEST, ex.reason().errorCode());
  }

  @ExceptionHandler(StaleBookingSettingsException.class)
  public ResponseEntity<ApiV2Problem> handleStaleBookingSettings() {
    return problem(HttpStatus.CONFLICT, "errors.api.v2.bookingConfiguration.stale");
  }

  @ExceptionHandler(ApiV2ResourceException.class)
  public ResponseEntity<ApiV2Problem> handleResourceException(ApiV2ResourceException ex) {
    String detail = messages.getMessage(ex.errorCode(), ex.arguments());
    return ApiV2Problem.response(ex.status(), detail, ex.errorCode(), detail);
  }

  @ExceptionHandler(DocumentValidationException.class)
  public ResponseEntity<ApiV2Problem> handleDocumentValidation(DocumentValidationException ex) {
    String detail = messages.getMessage(ex.getErrorKey());
    return ApiV2Problem.response(
        HttpStatus.BAD_REQUEST,
        detail,
        ex.getErrorKey(),
        detail,
        ex.getViolations().stream()
            .map(
                violation ->
                    new ApiV2Problem.InvalidParam(violation.field(), violation.reason().code()))
            .toList());
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiV2Problem> handleConstraintViolation() {
    return problem(HttpStatus.BAD_REQUEST, "errors.api.v2.invalidRequest");
  }

  @ExceptionHandler(CollectionQueryException.class)
  public ResponseEntity<ApiV2Problem> handleCollectionQuery(CollectionQueryException ex) {
    String key =
        switch (ex.getReason()) {
          case SYNTAX -> "errors.api.v2.query.syntax";
          case FIELD -> "errors.api.v2.query.field";
          case OPERATOR -> "errors.api.v2.query.operator";
          case VALUE -> "errors.api.v2.query.value";
          case COMPLEXITY -> "errors.api.v2.query.complexity";
        };
    return problem(HttpStatus.BAD_REQUEST, key);
  }

  @ExceptionHandler(CollectionMutationException.class)
  public ResponseEntity<ApiV2Problem> handleCollectionMutation(CollectionMutationException ex) {
    return switch (ex.getReason()) {
      case FILTER_REQUIRED -> problem(HttpStatus.BAD_REQUEST, "errors.api.v2.bulk.filter.required");
      case BULK_LIMIT -> problem(HttpStatus.UNPROCESSABLE_ENTITY, "errors.api.v2.bulk.limit");
    };
  }

  // Handler mapping raises 405s before a HandlerMethod exists, so
  // ApiV2PreHandlerProblemResolver routes them through handleUnexpected.

  @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
  public ResponseEntity<ApiV2Problem> handleNotAcceptable() {
    return problem(HttpStatus.NOT_ACCEPTABLE, "errors.api.v2.notAcceptable");
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiV2Problem> handleUnsupportedMediaType() {
    return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "errors.api.v2.unsupportedMediaType");
  }

  @ExceptionHandler(ServletRequestBindingException.class)
  public ResponseEntity<ApiV2Problem> handleMissingParameter() {
    return problem(HttpStatus.BAD_REQUEST, "errors.api.v2.missingParameter");
  }

  /**
   * Headers are preserved. Rebuilding the response from the status alone silently dropped every
   * header the exception declared, which is how a resource-level 405 ended up without the {@code
   * Allow} that RFC 9110 requires while the Spring-raised one kept it.
   */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiV2Problem> handleResponseStatus(ResponseStatusException ex) {
    HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
    if (status == null) {
      return problem(HttpStatus.INTERNAL_SERVER_ERROR, "errors.api.v2.unexpected");
    }
    return withHeaders(problem(status, messageKeyFor(status)), ex.getHeaders());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiV2Problem> handleUnexpected(Exception ex) {
    HttpStatus declared =
        ex instanceof ErrorResponse errorResponse
            ? HttpStatus.resolve(errorResponse.getStatusCode().value())
            : null;
    if (declared == null) {
      log.error("Unexpected REST API v2 error", ex);
      return problem(HttpStatus.INTERNAL_SERVER_ERROR, "errors.api.v2.unexpected");
    }
    // Preserve Spring's status, including retryable 5xx responses; replace only the detail.
    if (declared.is5xxServerError()) {
      log.error("REST API v2 request failed with {}", declared, ex);
    } else {
      log.debug("Mapping Spring MVC {} to {}", ex.getClass().getSimpleName(), declared);
    }
    return withHeaders(
        problem(declared, messageKeyFor(declared)), ((ErrorResponse) ex).getHeaders());
  }

  private static String messageKeyFor(HttpStatus status) {
    if (status.is5xxServerError()) {
      return "errors.api.v2.unexpected";
    }
    return switch (status) {
      case NOT_FOUND -> "errors.api.v2.notFound";
      case METHOD_NOT_ALLOWED -> "errors.api.v2.methodNotAllowed";
      case NOT_ACCEPTABLE -> "errors.api.v2.notAcceptable";
      case UNSUPPORTED_MEDIA_TYPE -> "errors.api.v2.unsupportedMediaType";
      case UNAUTHORIZED -> "errors.api.v2.authenticationRequired";
      case FORBIDDEN -> "errors.api.v2.forbidden";
      case TOO_MANY_REQUESTS -> "errors.api.v2.tooManyRequests";
      case BAD_REQUEST -> "errors.api.v2.invalidRequest";
      default -> "errors.api.v2.requestRejected";
    };
  }

  private static ResponseEntity<ApiV2Problem> withHeaders(
      ResponseEntity<ApiV2Problem> response, HttpHeaders extra) {
    if (extra.isEmpty()) {
      return response;
    }
    HttpHeaders headers = new HttpHeaders();
    headers.putAll(response.getHeaders());
    headers.putAll(extra);
    return new ResponseEntity<>(response.getBody(), headers, response.getStatusCode());
  }

  private ResponseEntity<ApiV2Problem> problem(HttpStatus status, String messageKey) {
    String detail = messages.getMessage(messageKey);
    return ApiV2Problem.response(status, detail, messageKey, detail);
  }

  private static Object principal(HttpServletRequest request) {
    ApiV2Caller caller = ApiV2Caller.from(request);
    if (caller == null) {
      return null;
    }
    if (caller.isDelegated()) {
      return caller.actor().getUsername() + " as " + caller.subject().getUsername();
    }
    return caller.subject().getUsername();
  }
}
