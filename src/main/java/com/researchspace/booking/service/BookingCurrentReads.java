package com.researchspace.booking.service;

import jakarta.persistence.OptimisticLockException;
import java.sql.SQLException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.orm.hibernate5.HibernateJdbcException;

/** Converts a database-rejected scheduling snapshot into a retryable booking conflict. */
final class BookingCurrentReads {
  private static final Logger log = LoggerFactory.getLogger(BookingCurrentReads.class);

  private BookingCurrentReads() {}

  static <T> T read(Supplier<T> query) {
    try {
      return query.get();
    } catch (HibernateJdbcException | PessimisticLockingFailureException exception) {
      // MariaDB rejects some current reads against an older snapshot with ER_CHECKREAD.
      if (!isSnapshotRejection(exception)) throw exception;
      log.warn("Scheduling snapshot changed during a locking read", exception);
      throw new BookingConcurrentModificationException();
    } catch (OptimisticLockException | ObjectOptimisticLockingFailureException exception) {
      // A locking read found a newer version than the entity cached from the initial snapshot.
      log.warn("Scheduling state changed after its initial read", exception);
      throw new BookingConcurrentModificationException();
    }
  }

  private static boolean isSnapshotRejection(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql && sql.getErrorCode() == 1020) return true;
    }
    return false;
  }
}
