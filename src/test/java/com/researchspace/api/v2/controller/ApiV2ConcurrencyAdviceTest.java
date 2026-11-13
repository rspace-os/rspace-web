package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.service.MessageSourceUtils;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
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
    var advice = new ApiV2ControllerAdvice(messages, new ApiV2ProblemFactory(messages));
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
  void classifiesCommitFailuresWithAndWithoutPreconditions() {
    MessageSourceUtils messages = mock(MessageSourceUtils.class);
    when(messages.getMessage("errors.concurrentChange")).thenReturn("Concurrent change");
    var advice = new ApiV2ControllerAdvice(messages, new ApiV2ProblemFactory(messages));
    for (Exception failure :
        List.of(
            new SQLException("race", "40001", 1213),
            new SQLException("duplicate", "23000", 1062),
            new SQLException("race", "HY000", 1020),
            new SQLException("timeout", "HY000", 1205),
            new OptimisticLockException(),
            new LockTimeoutException(),
            new PessimisticLockException(),
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
