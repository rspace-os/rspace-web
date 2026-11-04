package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.service.MessageSourceUtils;
import jakarta.persistence.OptimisticLockException;
import java.sql.SQLException;
import java.util.List;
import org.hibernate.StaleStateException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class ApiV2ConcurrencyAdviceTest {
  @Test
  void staleDeleteDistinguishesMissingRowsFromFailedPreconditions() {
    MessageSourceUtils messages = mock(MessageSourceUtils.class);
    when(messages.getMessage("errors.concurrentChange")).thenReturn("Concurrent change");
    var advice = new ApiV2ControllerAdvice(messages);
    for (boolean conditional : List.of(false, true)) {
      var request = new MockHttpServletRequest("DELETE", "/api/v2/maintenances/1");
      if (conditional) request.addHeader("If-Match", "\"1\"");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
      try {
        assertEquals(
            conditional ? HttpStatus.PRECONDITION_FAILED : HttpStatus.NOT_FOUND,
            advice.handleUnexpected(new OptimisticLockException()).getStatusCode());
      } finally {
        RequestContextHolder.resetRequestAttributes();
      }
    }
  }

  @Test
  void bodyVersionIsRetainedWithoutAnIfMatchHeader() {
    MessageSourceUtils messages = mock(MessageSourceUtils.class);
    when(messages.getMessage("errors.concurrentChange")).thenReturn("Concurrent change");
    var request = new MockHttpServletRequest("PATCH", "/api/v2/maintenances/1");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    try {
      var body =
          new com.researchspace.maintenance.model.ScheduledMaintenance(
              new java.util.Date(1), new java.util.Date(2));
      new ApiV2VersionPreconditionAdvice().afterBodyRead(body, null, null, null, null);
      assertEquals(
          HttpStatus.PRECONDITION_FAILED,
          new ApiV2ControllerAdvice(messages)
              .handleUnexpected(new OptimisticLockException())
              .getStatusCode());
    } finally {
      RequestContextHolder.resetRequestAttributes();
    }
  }

  @Test
  void classifiesCommitFailuresWithAndWithoutPreconditions() {
    MessageSourceUtils messages = mock(MessageSourceUtils.class);
    when(messages.getMessage("errors.concurrentChange")).thenReturn("Concurrent change");
    var advice = new ApiV2ControllerAdvice(messages);
    for (Exception failure :
        List.of(
            new SQLException("race", "40001", 1213),
            new SQLException("duplicate", "23000", 1062),
            new SQLException("race", "HY000", 1020),
            new SQLException("timeout", "HY000", 1205),
            new OptimisticLockException(),
            new jakarta.persistence.LockTimeoutException(),
            new jakarta.persistence.PessimisticLockException(),
            new StaleStateException("stale"),
            new CannotAcquireLockException("locked"))) {
      for (boolean conditional : List.of(false, true)) {
        var request = new MockHttpServletRequest("PATCH", "/api/v2/resources/1");
        if (conditional) request.addHeader("If-Match", "\"1\"");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
          assertEquals(
              conditional ? HttpStatus.PRECONDITION_FAILED : HttpStatus.CONFLICT,
              advice
                  .handleUnexpected(new TransactionSystemException("commit", failure))
                  .getStatusCode());
        } finally {
          RequestContextHolder.resetRequestAttributes();
        }
      }
    }
  }
}
