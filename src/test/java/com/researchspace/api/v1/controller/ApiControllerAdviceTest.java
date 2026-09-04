package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.auth.ApiAuthenticationException;
import com.researchspace.apiutils.ApiError;
import com.researchspace.apiutils.BindErrorList;
import com.researchspace.service.FilestoreOperationForbiddenException;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.chemistry.ChemistryClientException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import org.hibernate.StaleObjectStateException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.hibernate5.HibernateJdbcException;
import org.springframework.orm.hibernate5.HibernateOptimisticLockingFailureException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

class ApiControllerAdviceTest {

  @Test
  void bindErrorsAreResolvedCentrally() {
    MessageSourceUtils messages = new MessageSourceUtils(new JsonMessageSource());
    ApiControllerAdvice advice = new ApiControllerAdvice();
    advice.messages = messages;
    BeanPropertyBindingResult errors = new BeanPropertyBindingResult(new TestForm(), "fieldmark");
    errors.rejectValue("notebookId", "apps.fieldmark.errors.notebookIdRequired");
    errors.reject("apps.fieldmark.errors.fetchNotebooks");
    FieldError fieldError = errors.getFieldError();
    ObjectError globalError = errors.getGlobalError();
    ApiError apiError = advice.getApiErrorFromBindException(new BindException(errors));

    assertNull(fieldError.getDefaultMessage());
    assertNull(globalError.getDefaultMessage());
    assertEquals(
        List.of(
            "notebookId: Error importing notebook because the request had an empty \"notebookId\"",
            "fieldmark: Error fetching notebooks due to the Fieldmark server"),
        apiError.getErrors());
    assertEquals("Errors detected: 2", apiError.getMessage());
    BindErrorList errorList = (BindErrorList) apiError.getData();
    assertEquals(
        "Error importing notebook because the request had an empty \"notebookId\"",
        errorList.getValidationErrors().get(0).getMessage());
    assertEquals(
        "Error fetching notebooks due to the Fieldmark server",
        errorList.getValidationErrors().get(1).getMessage());
  }

  private static class TestForm {
    public String getNotebookId() {
      return null;
    }
  }

  /**
   * Locks in that the filestore delete gate's exception maps to HTTP 403 (per RSDEV-1110's
   * acceptance criteria) rather than the 401 that Shiro's AuthorizationException would produce.
   */
  @Test
  void filestoreOperationForbidden_mapsTo403() {
    ApiControllerAdvice advice = new ApiControllerAdvice();

    ResponseEntity<Object> response =
        advice.handleFilestoreOperationForbidden(
            new FilestoreOperationForbiddenException("not your file"), null);

    assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
  }

  @Test
  void chemistryMessageIsResolvedAtApiBoundary() {
    ApiControllerAdvice advice = new ApiControllerAdvice();
    advice.messages = new MessageSourceUtils(new JsonMessageSource());
    ChemistryClientException exception =
        new ChemistryClientException("errors.chemistry.searchRequestFailed", new Object[] {503});

    ResponseEntity<Object> response = advice.handleChemistryClientException(exception, null);

    ApiError error = (ApiError) response.getBody();
    assertEquals(
        "Unsuccessful search request to the chemistry service, status code: 503.",
        error.getMessage());
  }

  @Test
  void authenticationMessageIsResolvedAtApiBoundary() {
    ApiControllerAdvice advice = new ApiControllerAdvice();
    advice.messages = new MessageSourceUtils(new JsonMessageSource());

    ResponseEntity<Object> response =
        advice.handleAuth(new ApiAuthenticationException("oauth.errors.invalidCredentials"), null);

    ApiError error = (ApiError) response.getBody();
    assertEquals("Invalid user credentials.", error.getMessage());
  }

  /**
   * Locks in that a deadlock/lock-timeout Hibernate detects at commit time - already classified by
   * Spring as CannotAcquireLockException - maps to 409, not the 500 it fell through to before
   * RSDEV-1231.
   */
  @Test
  void cannotAcquireLockException_mapsTo409() {
    ApiControllerAdvice advice = new ApiControllerAdvice();
    advice.messages = new MessageSourceUtils(new JsonMessageSource());

    ResponseEntity<Object> response =
        advice.handleConcurrencyFailure(
            new CannotAcquireLockException(
                "could not execute statement",
                new SQLException("Deadlock found when trying to get lock", "40001", 1213)),
            null);

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    ApiError error = (ApiError) response.getBody();
    assertEquals(
        "Another request modified this item at the same time; retry the request.",
        error.getMessage());
  }

  /**
   * CannotAcquireLockException is one of several shapes a contended write takes, and they are
   * siblings rather than subclasses of one another: a lock-wait timeout and the stale-row failure
   * the locked reads added in RSDEV-1231 raise arrive as different types. All are retryable, so the
   * API tier maps the whole Spring category, matching the web tier.
   */
  @Test
  void theOtherConcurrencyFailuresAlsoMapTo409() {
    ApiControllerAdvice advice = new ApiControllerAdvice();
    advice.messages = new MessageSourceUtils(new JsonMessageSource());

    assertEquals(
        HttpStatus.CONFLICT,
        advice
            .handleConcurrencyFailure(new PessimisticLockingFailureException("lock wait"), null)
            .getStatusCode());
    assertEquals(
        HttpStatus.CONFLICT,
        advice
            .handleConcurrencyFailure(
                new HibernateOptimisticLockingFailureException(
                    new StaleObjectStateException("SubSample", 1L)),
                null)
            .getStatusCode());
  }

  /**
   * Locks in that Spring's uncategorized-JDBC fallback maps to 409 specifically for the MariaDB
   * error code seen from a concurrent-write conflict (1020, "Record has changed since last read"),
   * not for every HibernateJdbcException regardless of cause (RSDEV-1231).
   */
  @Test
  void hibernateJdbcException_withConcurrentWriteErrorCode_mapsTo409() {
    ApiControllerAdvice advice = new ApiControllerAdvice();
    advice.messages = new MessageSourceUtils(new JsonMessageSource());
    HibernateJdbcException ex = mock(HibernateJdbcException.class);
    when(ex.getSQLException())
        .thenReturn(new SQLException("Record has changed since last read", "HY000", 1020));

    ResponseEntity<Object> response = advice.handleHibernateJdbcException(ex, null);

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
  }

  @Test
  void hibernateJdbcException_withUnrelatedErrorCode_stillMapsTo500() {
    ApiControllerAdvice advice = new ApiControllerAdvice();
    HibernateJdbcException ex = mock(HibernateJdbcException.class);
    when(ex.getSQLException())
        .thenReturn(new SQLException("Data too long for column", "22001", 1406));

    advice.messages = new MessageSourceUtils(new JsonMessageSource());

    ResponseEntity<Object> response = advice.handleHibernateJdbcException(ex, null);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    // The detail text ships in the response body, so it comes from the catalog like every other
    // message this advice produces, not from an English literal in the handler.
    ApiError error = (ApiError) response.getBody();
    assertEquals(List.of("General server error"), error.getErrors());
  }

  /**
   * This handler is registered for every @ApiController, not just the Inventory operations
   * endpoint, so a lock conflict on any API resource resolves it. Its message therefore lives in
   * the cross-cutting catalog rather than under errors.inventory.operation.
   */
  @Test
  void concurrentUpdateMessageIsCrossCuttingNotInventorySpecific() throws java.io.IOException {
    ApiControllerAdvice advice = new ApiControllerAdvice();
    advice.messages = new MessageSourceUtils(new JsonMessageSource());

    ApiError error =
        (ApiError)
            advice
                .handleConcurrencyFailure(
                    new CannotAcquireLockException(
                        "could not execute statement",
                        new SQLException("Deadlock found", "40001", 1213)),
                    null)
                .getBody();

    assertEquals(
        "Another request modified this item at the same time; retry the request.",
        error.getMessage());
    assertFalse(
        Files.readString(
                Path.of(
                    "src/main/webapp/ui/src/modules/common/i18n/locales/en-US/server.inventory.json"))
            .contains("concurrentUpdate"),
        "the message is not inventory-specific, so it must not live in the inventory catalog");
  }
}
