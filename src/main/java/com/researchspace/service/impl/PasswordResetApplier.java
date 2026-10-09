package com.researchspace.service.impl;

import com.researchspace.model.TokenBasedVerification;
import com.researchspace.service.UserManager;
import java.sql.SQLException;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Runs a reset-link password change and tells a lost race apart from other database failures. Not
 * transactional, so the wrapped change has committed or rolled back before its outcome is read.
 */
@Service
public class PasswordResetApplier {

  /** "Record has changed since last read", raised under MariaDB's innodb_snapshot_isolation. */
  private static final int MARIADB_SNAPSHOT_CONFLICT = 1020;

  public enum Outcome {
    APPLIED,
    REFUSED,
    CONFLICT
  }

  public record ResetAttempt(Outcome outcome, TokenBasedVerification change) {}

  private final UserManager userManager;

  @Autowired
  public PasswordResetApplier(UserManager userManager) {
    this.userManager = userManager;
  }

  /**
   * Runs the change for a reset token.
   *
   * @return {@code APPLIED} with the used token; {@code REFUSED} when the change returned null (the
   *     token was unusable at the moment of change); {@code CONFLICT} when a concurrent change made
   *     the database refuse it
   * @throws DataAccessException any other database failure, unchanged
   */
  public ResetAttempt apply(String token, Supplier<TokenBasedVerification> change) {
    TokenBasedVerification applied;
    try {
      applied = change.get();
    } catch (DataAccessException e) {
      if (isSnapshotConflict(e) || isResetCompletedNow(token)) {
        return new ResetAttempt(Outcome.CONFLICT, null);
      }
      throw e;
    }
    return applied == null
        ? new ResetAttempt(Outcome.REFUSED, null)
        : new ResetAttempt(Outcome.APPLIED, applied);
  }

  private static boolean isSnapshotConflict(DataAccessException e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof SQLException sql && sql.getErrorCode() == MARIADB_SNAPSHOT_CONFLICT) {
        return true;
      }
    }
    return false;
  }

  private boolean isResetCompletedNow(String token) {
    TokenBasedVerification reread = userManager.getUserVerificationToken(token);
    return reread != null && reread.isResetCompleted();
  }
}
