package com.researchspace.booking.service;

import com.researchspace.api.ConcurrentChangeClassifier;
import java.sql.SQLException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Converts a database-rejected scheduling snapshot into a retryable booking conflict. */
public final class BookingCurrentReads {
  private static final Logger log = LoggerFactory.getLogger(BookingCurrentReads.class);

  private BookingCurrentReads() {}

  public static <T> T read(Supplier<T> query) {
    try {
      return query.get();
    } catch (RuntimeException exception) {
      if (!ConcurrentChangeClassifier.isConcurrentChange(exception)
          || (!isSnapshotRejection(exception) && !ConcurrentChangeClassifier.isStale(exception))) {
        throw exception;
      }
      log.warn("Scheduling state changed during a locking read", exception);
      throw new BookingConcurrentModificationException(exception);
    }
  }

  private static boolean isSnapshotRejection(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql && sql.getErrorCode() == 1020) return true;
    }
    return false;
  }
}
