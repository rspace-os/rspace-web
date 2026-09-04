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
import com.researchspace.service.FilestoreOperationForbiddenException;
import com.researchspace.service.MediaContentMismatchException;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.archive.export.ExportFailureException;
import com.researchspace.service.chemistry.ChemistryClientException;
import com.researchspace.service.chemistry.StoichiometryException;
import jakarta.ws.rs.NotFoundException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.hibernate5.HibernateJdbcException;
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

  /**
   * MariaDB/InnoDB error codes seen from two transactions writing the same row(s) at once (RSDEV-
   * 1231): 1020 is "Record has changed since last read", surfaced via {@link
   * HibernateJdbcException#getSQLException()} when Hibernate cannot classify the failure more
   * specifically. A genuine deadlock or lock-wait timeout is already classified by Hibernate itself
   * and reaches Spring as {@link CannotAcquireLockException} instead, so it needs no code check
   * here.
   */
  private static final Set<Integer> CONCURRENT_WRITE_SQL_ERROR_CODES = Set.of(1020);

  // 401
  @ExceptionHandler({AuthorizationException.class, ApiAuthenticationException.class})
  public ResponseEntity<Object> handleAuth(final Exception ex, final WebRequest request) {
    final String error = messages.getMessage("errors.authorization.apiError");
    final String message =
        ex instanceof ApiAuthenticationException authException
            ? messages.getMessage(authException.getMessageKey(), authException.getArgs())
            : ex.getLocalizedMessage();
    final ApiError apiError =
        new ApiError(HttpStatus.UNAUTHORIZED, ApiErrorCodes.AUTH.getCode(), message, error);
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

  // 409: Spring's own recognized categories for a contended write. Hibernate has already
  // classified these specifically, so every occurrence is a conflict worth retrying (RSDEV-1231).
  // Both categories are needed and neither implies the other: a deadlock and a lock-wait timeout
  // arrive as CannotAcquireLockException under PessimisticLockingFailureException, while the
  // stale-row failure a locked read raises is an OptimisticLockingFailureException.
  @ResponseStatus(HttpStatus.CONFLICT)
  @ExceptionHandler({
    PessimisticLockingFailureException.class,
    OptimisticLockingFailureException.class
  })
  public ResponseEntity<Object> handleConcurrencyFailure(
      final DataAccessException ex, final WebRequest request) {
    return handleConcurrentUpdateConflict(ex);
  }

  // 409, conditionally: Spring's fallback for a Hibernate JDBC failure it could not classify more
  // specifically covers far more than concurrency conflicts, so only the known concurrent-write SQL
  // error codes are treated as a conflict; anything else still falls through as a 500, unchanged
  // (RSDEV-1231).
  @ExceptionHandler(HibernateJdbcException.class)
  public ResponseEntity<Object> handleHibernateJdbcException(
      final HibernateJdbcException ex, final WebRequest request) {
    SQLException sqlException = ex.getSQLException();
    if (sqlException != null
        && CONCURRENT_WRITE_SQL_ERROR_CODES.contains(sqlException.getErrorCode())) {
      return handleConcurrentUpdateConflict(ex);
    }
    return handle500Error(
        ex, ApiErrorCodes.GENERAL_ERROR, messages.getMessage("api.errors.generalServerError"));
  }

  private ResponseEntity<Object> handleConcurrentUpdateConflict(final DataAccessException ex) {
    logException(ex);
    final ApiError apiError =
        new ApiError(
            HttpStatus.CONFLICT,
            ApiErrorCodes.EDIT_CONFLICT.getCode(),
            messages.getMessage("api.errors.concurrentUpdate"),
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
  @ExceptionHandler({FilestoreOperationForbiddenException.class})
  public ResponseEntity<Object> handleFilestoreOperationForbidden(
      final FilestoreOperationForbiddenException ex, final WebRequest request) {
    log.warn("filestore operation forbidden: {}", ex.getMessage());
    final ApiError apiError =
        new ApiError(
            HttpStatus.FORBIDDEN, ApiErrorCodes.AUTH.getCode(), ex.getLocalizedMessage(), "");
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

  // 422 with errorCode
  @ExceptionHandler({MediaContentMismatchException.class})
  public ResponseEntity<Object> handleMediaContentMismatch(
      final MediaContentMismatchException ex, final WebRequest request) {
    log.warn("rejected upload: {}", StringUtils.join(ex.getArgs(), ", "));
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
    return handle500Error(
        ex, ApiErrorCodes.BATCH_LAUNCH, messages.getMessage("export.errors.batchLaunchFailure"));
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

  @ExceptionHandler(ChemistryClientException.class)
  public ResponseEntity<Object> handleChemistryClientException(
      ChemistryClientException ex, WebRequest request) {
    HttpStatus status = ex.getStatus() != null ? ex.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
    String resolvedMessage = messages.getMessage(ex.getMessageKey(), ex.getArgs());
    ApiError apiError = new ApiError(status, 50001, resolvedMessage, "");
    return new ResponseEntity<>(apiError, new HttpHeaders(), apiError.getStatus());
  }

  @Override
  @ExceptionHandler(BindException.class)
  protected ResponseEntity<Object> handleBindException(
      final BindException ex, final WebRequest request) {

    logException(ex);
    final ApiError apiError = getApiErrorFromBindException(ex);
    return handleExceptionInternal(ex, apiError, new HttpHeaders(), apiError.getStatus(), request);
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
      errors.add(error.getObjectName() + ": " + resolvedMessage);
      bindErrors.add(new BindError(error, () -> resolvedMessage));
    }
    BindErrorList errorList = new BindErrorList(bindErrors);

    final ApiError apiError =
        new ApiError(
            HttpStatus.BAD_REQUEST,
            ApiErrorCodes.INVALID_FIELD.getCode(),
            messages.getMessage("api.errors.detected", new Object[] {ex.getErrorCount()}),
            errors,
            errorList);
    return apiError;
  }
}
