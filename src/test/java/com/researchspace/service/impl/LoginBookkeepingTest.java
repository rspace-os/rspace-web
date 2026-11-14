package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.webapp.filter.DefaultLockoutPolicy;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class LoginBookkeepingTest {
  private final UserDao users = mock(UserDao.class);
  private LoginBookkeeping bookkeeping;

  private static class Policy extends DefaultLockoutPolicy {
    Policy() {
      setTimeout(10_000);
      setLockoutDuration(10_000);
    }
  }

  @BeforeEach
  void setup() {
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    when(transactions.getTransaction(any()))
        .thenAnswer(invocation -> new SimpleTransactionStatus());
    bookkeeping = new LoginBookkeeping(users, new Policy(), transactions);
  }

  private User state(int count, boolean locked, long age) {
    User user = new User();
    user.setId(42L);
    user.setNumConsecutiveLoginFailures((byte) count);
    user.setAccountLocked(locked);
    user.setLoginFailure(age < 0 ? null : new Date(System.currentTimeMillis() - age));
    return user;
  }

  private User failure(User before) {
    when(users.readLoginState("user")).thenReturn(before);
    when(users.updateLoginState(eq(before), any(), isNull())).thenReturn(true);
    bookkeeping.recordFailure("user");
    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).updateLoginState(eq(before), saved.capture(), isNull());
    verify(users, never()).lockLoginAccount(any());
    return saved.getValue();
  }

  @Test
  void firstFailureStartsTheWindow() {
    User saved = failure(state(0, false, -1));
    assertEquals(1, saved.getNumConsecutiveLoginFailures());
    assertFalse(saved.isAccountLocked());
    assertNotNull(saved.getLoginFailure());
  }

  @Test
  void outsideFailureWindowDoesNotLock() {
    User saved = failure(state(3, false, 15_000));
    assertEquals(4, saved.getNumConsecutiveLoginFailures());
    assertFalse(saved.isAccountLocked());
    assertTrue(saved.getLoginFailure().getTime() > System.currentTimeMillis() - 2_000);
  }

  @Test
  void thresholdFailureLocksThroughTheGuardedUpdate() {
    User saved = failure(state(3, false, 0));
    assertEquals(4, saved.getNumConsecutiveLoginFailures());
    assertTrue(saved.isAccountLocked());
  }

  @Test
  void expiredFailureUnlocksAndStartsAtOne() {
    User saved = failure(state(4, true, 30_000));
    assertFalse(saved.isAccountLocked());
    assertEquals(1, saved.getNumConsecutiveLoginFailures());
  }

  @Test
  void failureWhileLockedStaysLocked() {
    assertTrue(failure(state(4, true, 0)).isAccountLocked());
  }

  @Test
  void successPersistsExpiryUnlock() {
    when(users.readLoginState("user")).thenReturn(state(4, true, 30_000));
    when(users.updateLoginState(any(), any(), any())).thenReturn(true);
    User saved = bookkeeping.recordSuccess("user");
    assertFalse(saved.isAccountLocked());
    assertEquals(0, saved.getNumConsecutiveLoginFailures());
    assertNull(saved.getLoginFailure());
    assertNotNull(saved.getLastLogin());
    verify(users).updateLoginState(any(), any(), eq(saved.getLastLogin()));
  }

  @Test
  void successKeepsAnUnexpiredLock() {
    when(users.readLoginState("user")).thenReturn(state(4, true, 0));
    when(users.updateLoginState(any(), any(), any())).thenReturn(true);
    assertTrue(bookkeeping.recordSuccess("user").isAccountLocked());
  }

  @Test
  void guardConflictRereadsAndRecomputes() {
    when(users.readLoginState("user")).thenReturn(state(0, false, -1), state(1, false, 0));
    when(users.updateLoginState(any(), any(), isNull())).thenReturn(false, true);
    bookkeeping.recordFailure("user");
    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users, times(2)).updateLoginState(any(), saved.capture(), isNull());
    assertEquals(2, saved.getValue().getNumConsecutiveLoginFailures());
    verify(users, never()).lockLoginAccount(any());
  }

  @Test
  void exhaustedFailureGuardsFallBackToRowLock() {
    when(users.readLoginState("user")).thenReturn(state(0, false, -1));
    User current = state(2, false, 0);
    when(users.lockLoginAccount(42L)).thenReturn(current);
    bookkeeping.recordFailure("user");
    verify(users, times(3)).updateLoginState(any(), any(), isNull());
    assertEquals(3, current.getNumConsecutiveLoginFailures());
    assertFalse(current.isAccountLocked());
  }

  @Test
  void exhaustedSuccessGuardsFallBackToRowLock() {
    when(users.readLoginState("user")).thenReturn(state(2, false, 0));
    User current = state(3, false, 0);
    when(users.lockLoginAccount(42L)).thenReturn(current);
    User saved = bookkeeping.recordSuccess("user");
    verify(users, times(3)).updateLoginState(any(), any(), any());
    assertSame(current, saved);
    assertEquals(0, current.getNumConsecutiveLoginFailures());
    assertNotNull(current.getLastLogin());
  }
}
