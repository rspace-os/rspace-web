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
  /** MariaDB ER_DUP_ENTRY: a concurrent insert claimed the same unique key. */
  private static final int ER_DUP_ENTRY = 1062;

  /**
   * MariaDB ER_CHECKREAD: the row changed after this transaction's snapshot. Raised when {@code
   * innodb_snapshot_isolation} is on; MariaDB rolls back the whole transaction.
   */
  private static final int ER_CHECKREAD = 1020;

  /** MariaDB ER_LOCK_DEADLOCK: InnoDB chose this transaction as a deadlock victim. */
  private static final int ER_LOCK_DEADLOCK = 1213;

  /** MariaDB ER_LOCK_WAIT_TIMEOUT: {@code innodb_lock_wait_timeout} expired waiting for a lock. */
  private static final int ER_LOCK_WAIT_TIMEOUT = 1205;

  /** SQL standard SQLSTATE for a serialization failure, including deadlock rollbacks. */
  private static final String SQLSTATE_SERIALIZATION_FAILURE = "40001";

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
          && (sql.getErrorCode() == ER_DUP_ENTRY
              || sql.getErrorCode() == ER_CHECKREAD
              || sql.getErrorCode() == ER_LOCK_DEADLOCK
              || sql.getErrorCode() == ER_LOCK_WAIT_TIMEOUT
              || SQLSTATE_SERIALIZATION_FAILURE.equals(sql.getSQLState()))) {
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
