package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import java.sql.SQLException;
import org.hibernate.exception.GenericJDBCException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.hibernate5.HibernateJdbcException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The status this handler puts on an Ajax error response. Two transactions writing the same row
 * reach it (two browser tabs saving a UI preference, whose value is a single shared JSON column):
 * that is a conflict the caller can retry, not the "something broke" 500 everything else gets.
 */
public class ControllerExceptionHandlerTest {

  private ControllerExceptionHandler handler;

  @BeforeEach
  public void setUp() {
    handler = new ControllerExceptionHandler();
    ReflectionTestUtils.setField(
        handler, "messages", new MessageSourceUtils(new JsonMessageSource()));
  }

  private int statusFor(Exception e) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Requested-With", "XMLHttpRequest");
    MockHttpServletResponse response = new MockHttpServletResponse();
    handler.handleExceptions(request, response, e);
    return response.getStatus();
  }

  @Test
  public void aLockThatCouldNotBeAcquiredIsAConflict() {
    assertEquals(409, statusFor(new CannotAcquireLockException("deadlock")));
  }

  @Test
  public void aConcurrentWriteSqlErrorIsAConflict() {
    // 1020, "Record has changed since last read": MariaDB 11.6+ raises it at commit under snapshot
    // isolation, and Hibernate cannot classify it, so it arrives as the generic JDBC wrapper.
    assertEquals(409, statusFor(hibernateJdbcExceptionWithSqlErrorCode(1020)));
  }

  @Test
  public void anUnrelatedJdbcErrorIsStillAServerError() {
    assertEquals(500, statusFor(hibernateJdbcExceptionWithSqlErrorCode(1146)));
  }

  private HibernateJdbcException hibernateJdbcExceptionWithSqlErrorCode(int errorCode) {
    SQLException sqlException = new SQLException("db said no", "HY000", errorCode);
    return new HibernateJdbcException(new GenericJDBCException("failed", sqlException));
  }
}
