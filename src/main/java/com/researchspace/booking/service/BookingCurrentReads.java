package com.researchspace.booking.service;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.hibernate5.HibernateJdbcException;

/** Converts a database-rejected scheduling snapshot into a retryable booking conflict. */
final class BookingCurrentReads {
  private static final Logger log = LoggerFactory.getLogger(BookingCurrentReads.class);

  private BookingCurrentReads() {}

  static <T> T read(Supplier<T> query) {
    try {
      return query.get();
    } catch (HibernateJdbcException exception) {
      // MariaDB rejects some current reads against an older snapshot with ER_CHECKREAD.
      if (exception.getSQLException().getErrorCode() != 1020) throw exception;
      log.warn("Scheduling snapshot changed during a locking read", exception);
      throw new BookingConcurrentModificationException();
    }
  }
}
