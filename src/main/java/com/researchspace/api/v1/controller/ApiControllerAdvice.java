package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.auth.ApiAuthenticationException;
import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.throttling.FileUploadLimitExceededException;
import com.researchspace.apiutils.ApiError;
import com.researchspace.apiutils.ApiErrorCodes;
import com.researchspace.apiutils.BindError;
import com.researchspace.apiutils.BindErrorList;
import com.researchspace.apiutils.RestControllerAdvice;
import com.researchspace.core.util.throttling.TooManyRequestsException;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.archive.export.ExportFailureException;
import com.researchspace.service.chemistry.StoichiometryException;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;

/** Specific exception handler for API RestController */
@ControllerAdvice(annotations = ApiController.class)
@Slf4j
public class ApiControllerAdvice extends RestControllerAdvice {

  protected @Autowired MessageSourceUtils messages;

  // 401
  @ExceptionHandler({AuthorizationException.class, ApiAuthenticationException.class})
  public ResponseEntity<Object> handleAuth(final Exception ex, final WebRequest request) {
    final String error = "Authorisation error";
    final ApiError apiError =
        new ApiError(
            HttpStatus.UNAUTHORIZED, ApiErrorCodes.AUTH.getCode(), ex.getLocalizedMessage(), error);
    return new ResponseEntity<Object>(apiError, new HttpHeaders(), apiError.getStatus());
  }

  @ExceptionHandler({NotFoundException.class, UnsupportedOperationException.class})
  public ResponseEntity<Object> handleResourceNotFound(
      final Exception ex, final WebRequest request) {
    log.error("error", ex);
    final ApiError apiError =
        new ApiError(
            HttpStatus.NOT_FOUND,
            ApiErrorCodes.CONFIGURED_UNAVAILABLE.getCode(),
            ex.getLocalizedMessage(),
            "");
    return new ResponseEntity<Object>(apiError, new HttpHeaders(), apiError.getStatus());
  }

  @ResponseStatus(HttpStatus.CONFLICT)
  @ExceptionHandler(DocumentAlreadyEditedException.class)
  protected ResponseEntity<Object> handleDocumentAlreadyEditedException(
      final DocumentAlreadyEditedException ex, final WebRequest request) {
    logException(ex);
    final ApiError apiError =
        new ApiError(
            HttpStatus.CONFLICT,
            ApiErrorCodes.EDIT_CONFLICT.getCode(),
            ex.getLocalizedMessage(),
            "");
    return new ResponseEntity<Object>(apiError, new HttpHeaders(), apiError.getStatus());
  }

  // 403
  @ExceptionHandler({APIUnavailableException.class})
  public ResponseEntity<Object> handleTooManyRequests(
      final APIUnavailableException ex, final WebRequest request) {
    log.error("error", ex);
    final ApiError apiError =
        new ApiError(
            HttpStatus.FORBIDDEN,
            ApiErrorCodes.CONFIGURED_UNAVAILABLE.getCode(),
            ex.getLocalizedMessage(),
            "");
    return new ResponseEntity<Object>(apiError, new HttpHeaders(), apiError.getStatus());
  }

  // 403
  @ExceptionHandler({ExternalApiAuthorizationException.class})
  public ResponseEntity<Object> hanleExternalApiAuthorizationException(
      final ExternalApiAuthorizationException ex, final WebRequest request) {
    log.error("external API authorization exception", ex);
    final ApiError apiError =
        new ApiError(
            HttpStatus.FORBIDDEN, ApiErrorCodes.AUTH.getCode(), ex.getLocalizedMessage(), "");
    return new ResponseEntity<Object>(apiError, new HttpHeaders(), apiError.getStatus());
  }

  // 422 with errorCode
  @ExceptionHandler({ApiRuntimeException.class})
  public ResponseEntity<Object> handleApiRuntimeException(
      final ApiRuntimeException ex, final WebRequest request) {
    log.error("api runtime error: " + ex.getErrorCode() + ": " + StringUtils.join(ex.getArgs()));
    String resolvedMessage = messages.getMessage(ex.getErrorCode(), ex.getArgs());
    final ApiError apiError =
        new ApiError(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCodes.ILLEGAL_ARGUMENT.getCode(),
            resolvedMessage,
            ex.getErrorCode(),
            resolvedMessage);
    return new ResponseEntity<>(apiError, new HttpHeaders(), apiError.getStatus());
  }

  // 429
  @ExceptionHandler({TooManyRequestsException.class})
  public ResponseEntity<Object> handleUnsupported(
      final TooManyRequestsException ex, final WebRequest request) {
    log.error("error", ex);
    final ApiError apiError =
        new ApiError(
            HttpStatus.TOO_MANY_REQUESTS,
            ApiErrorCodes.TOOMANY_REQUESTS.getCode(),
            ex.getLocalizedMessage(),
            "");
    return new ResponseEntity<Object>(apiError, new HttpHeaders(), apiError.getStatus());
  }

  // 429
  @ExceptionHandler({FileUploadLimitExceededException.class})
  public ResponseEntity<Object> handleUnsupported(
      final FileUploadLimitExceededException ex, final WebRequest request) {
    log.error("error", ex);
    final ApiError apiError =
        new ApiError(
            HttpStatus.TOO_MANY_REQUESTS,
            ApiErrorCodes.MAX_FILE_UPLOAD_RATE_EXCEEDED.getCode(),
            ex.getLocalizedMessage(),
            "");
    return new ResponseEntity<Object>(apiError, new HttpHeaders(), apiError.getStatus());
  }

  @ExceptionHandler({ExportFailureException.class})
  public ResponseEntity<Object> handleUnsupported(
      final ExportFailureException ex, final WebRequest request) {
    return handle500Error(ex, ApiErrorCodes.BATCH_LAUNCH, "Export batch job launch failure");
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(StoichiometryException.class)
  public ResponseEntity<Object> handleStoichiometryException(
      StoichiometryException ex, WebRequest request) {
    log.error("Stoichiometry error", ex);
    ApiError apiError =
        new ApiError(
            HttpStatus.BAD_REQUEST,
            ApiErrorCodes.ILLEGAL_ARGUMENT.getCode(),
            ex.getLocalizedMessage(),
            "");
    return new ResponseEntity<>(apiError, new HttpHeaders(), apiError.getStatus());
  }

  @Override
  protected ResponseEntity<Object> handleBindException(
      final BindException ex,
      final HttpHeaders headers,
      final HttpStatus status,
      final WebRequest request) {

    logException(ex);
    final ApiError apiError = getApiErrorFromBindException(ex);
    return handleExceptionInternal(ex, apiError, headers, apiError.getStatus(), request);
  }

  public ApiError getApiErrorFromBindException(final BindException ex) {
    final List<String> errors = new ArrayList<>();
    List<BindError> bindErrors = new ArrayList<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      String resolvedMessage = messages.getMessage(error);
      errors.add(error.getField() + ": " + resolvedMessage);
      bindErrors.add(new BindError(error, () -> resolvedMessage));
    }

    for (ObjectError error : ex.getBindingResult().getGlobalErrors()) {
      String resolvedMessage = messages.getMessage(error);
      errors.add(error.getObjectName() + ": " + messages.getMessage(error));
      bindErrors.add(new BindError(error, () -> resolvedMessage));
    }
    BindErrorList errorList = new BindErrorList(bindErrors);

    final ApiError apiError =
        new ApiError(
            HttpStatus.BAD_REQUEST,
            ApiErrorCodes.INVALID_FIELD.getCode(),
            "Errors detected : " + ex.getErrorCount(),
            errors,
            errorList);
    return apiError;
  }
}
