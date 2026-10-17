package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.TokenBasedVerification;
import com.researchspace.model.TokenBasedVerificationType;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.PasswordResetApplier.Outcome;
import com.researchspace.service.impl.PasswordResetApplier.ResetAttempt;
import java.sql.SQLException;
import java.util.Date;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

class PasswordResetApplierTest {

  private static final String TOKEN = "token";

  private final UserManager userManager = mock(UserManager.class);
  private final PasswordResetApplier applier = new PasswordResetApplier(userManager);

  @Test
  void appliedChangeIsReturned() {
    TokenBasedVerification change = token(true);
    ResetAttempt attempt = applier.apply(TOKEN, () -> change);
    assertEquals(Outcome.APPLIED, attempt.outcome());
    assertSame(change, attempt.change());
  }

  @Test
  void nullChangeIsRefused() {
    assertEquals(Outcome.REFUSED, applier.apply(TOKEN, () -> null).outcome());
  }

  @Test
  void snapshotConflictInTheCauseChainIsAConflictWithoutReReadingTheToken() {
    assertEquals(Outcome.CONFLICT, applier.apply(TOKEN, throwing(dbFailure(1020))).outcome());
    verify(userManager, never()).getUserVerificationToken(TOKEN);
  }

  @Test
  void otherDbFailureWithTheTokenNowUsedIsAConflict() {
    when(userManager.getUserVerificationToken(TOKEN)).thenReturn(token(true));
    assertEquals(Outcome.CONFLICT, applier.apply(TOKEN, throwing(dbFailure(1205))).outcome());
  }

  @Test
  void otherDbFailureWithTheTokenStillUnusedIsRethrown() {
    when(userManager.getUserVerificationToken(TOKEN)).thenReturn(token(false));
    DataAccessException failure = dbFailure(1205);
    assertSame(
        failure,
        assertThrows(DataAccessException.class, () -> applier.apply(TOKEN, throwing(failure))));
  }

  private static Supplier<TokenBasedVerification> throwing(DataAccessException failure) {
    return () -> {
      throw failure;
    };
  }

  private static DataAccessException dbFailure(int errorCode) {
    return new DataIntegrityViolationException(
        "could not execute statement",
        new RuntimeException(
            new SQLException("Record has changed since last read", "HY000", errorCode)));
  }

  private static TokenBasedVerification token(boolean completed) {
    TokenBasedVerification token =
        new TokenBasedVerification(
            "reset@example.com", new Date(), TokenBasedVerificationType.PASSWORD_CHANGE);
    token.setResetCompleted(completed);
    return token;
  }
}
