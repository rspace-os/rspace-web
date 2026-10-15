package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.util.stream.Stream;
import org.hibernate.exception.GenericJDBCException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.orm.hibernate5.HibernateJdbcException;

class BookingCurrentReadsTest {

  @ParameterizedTest
  @MethodSource("convertedFailures")
  void convertsCurrentReadFailuresToBookingConflict(RuntimeException error) {
    assertThrows(
        BookingConcurrentModificationException.class,
        () ->
            BookingCurrentReads.read(
                () -> {
                  throw error;
                }));
  }

  private static Stream<Arguments> convertedFailures() {
    return Stream.of(
        Arguments.of(
            new HibernateJdbcException(
                new GenericJDBCException("changed", new SQLException("changed", "HY000", 1020)))),
        Arguments.of(
            new PessimisticLockingFailureException(
                "could not obtain pessimistic lock", new SQLException("changed", "HY000", 1020))),
        Arguments.of(new ObjectOptimisticLockingFailureException("BookingConfiguration", 4L)));
  }

  @ParameterizedTest
  @MethodSource("preservedFailures")
  void preservesOtherDatabaseFailures(RuntimeException error) {
    assertSame(
        error,
        assertThrows(
            RuntimeException.class,
            () ->
                BookingCurrentReads.read(
                    () -> {
                      throw error;
                    })));
  }

  private static Stream<Arguments> preservedFailures() {
    return Stream.of(
        Arguments.of(
            new PessimisticLockingFailureException(
                "deadlock", new SQLException("deadlock", "40001", 1213))),
        Arguments.of(
            new HibernateJdbcException(
                new GenericJDBCException("failure", new SQLException("failure", "HY000", 999)))));
  }
}
