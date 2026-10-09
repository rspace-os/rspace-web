package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import org.hibernate.exception.GenericJDBCException;
import org.junit.jupiter.api.Test;
import org.springframework.orm.hibernate5.HibernateJdbcException;

class BookingCurrentReadsTest {
  @Test
  void returnsSuccessfulRead() {
    assertEquals("current", BookingCurrentReads.read(() -> "current"));
  }

  @Test
  void convertsSnapshotRejectionToBookingConflict() {
    HibernateJdbcException error =
        new HibernateJdbcException(
            new GenericJDBCException("changed", new SQLException("changed", "HY000", 1020)));
    assertThrows(
        BookingConcurrentModificationException.class,
        () ->
            BookingCurrentReads.read(
                () -> {
                  throw error;
                }));
  }

  @Test
  void preservesOtherDatabaseFailures() {
    HibernateJdbcException error =
        new HibernateJdbcException(
            new GenericJDBCException("failure", new SQLException("failure", "HY000", 999)));
    assertSame(
        error,
        assertThrows(
            HibernateJdbcException.class,
            () ->
                BookingCurrentReads.read(
                    () -> {
                      throw error;
                    })));
  }
}
