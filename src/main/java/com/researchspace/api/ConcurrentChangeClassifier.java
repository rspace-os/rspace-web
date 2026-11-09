package com.researchspace.api;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.hibernate.StaleStateException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

/** Classifies infrastructure races, including failures wrapped by transaction commit. */
public final class ConcurrentChangeClassifier {
  private ConcurrentChangeClassifier() {}

  public static boolean isConcurrentChange(Throwable failure) {
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
      if (cause instanceof OptimisticLockException
          || cause instanceof LockTimeoutException
          || cause instanceof PessimisticLockException
          || cause instanceof StaleStateException
          || cause instanceof ConcurrencyFailureException
          || cause instanceof DuplicateKeyException) {
        return true;
      }
      if (cause instanceof SQLException sql
          && (sql.getErrorCode() == 1062
              || sql.getErrorCode() == 1020
              || sql.getErrorCode() == 1213
              || sql.getErrorCode() == 1205
              || "40001".equals(sql.getSQLState()))) {
        return true;
      }
    }
    return false;
  }

  public static boolean isStale(Throwable failure) {
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
      if (cause instanceof OptimisticLockException
          || cause instanceof StaleStateException
          || cause instanceof OptimisticLockingFailureException) {
        return true;
      }
    }
    return false;
  }
}
