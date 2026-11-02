package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.researchspace.booking.dao.BookingCalendarPermissionGateDao;
import com.researchspace.booking.service.BookingCalendarRevocationCoordinator;
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
  private final BookingCalendarRevocationCoordinator revocations =
      mock(BookingCalendarRevocationCoordinator.class);
  private final BookingCalendarPermissionGateDao gate =
      mock(BookingCalendarPermissionGateDao.class);
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
    bookkeeping = new LoginBookkeeping(users, new Policy(), revocations, gate, transactions);
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
    verifyNoInteractions(revocations);
    return saved.getValue();
  }

  @Test
  void ordinaryFailureSkipsGate() {
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
  void expiredFailureUnlocksAndStartsAtOne() {
    User saved = failure(state(4, true, 30_000));
    assertFalse(saved.isAccountLocked());
    assertEquals(1, saved.getNumConsecutiveLoginFailures());
  }

  @Test
  void stillLockedFailureSkipsGate() {
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
    verifyNoInteractions(revocations);
  }

  @Test
  void normalSuccessSkipsGate() {
    when(users.readLoginState("user")).thenReturn(state(1, false, 0));
    when(users.updateLoginState(any(), any(), any())).thenReturn(true);
    assertFalse(bookkeeping.recordSuccess("user").isAccountLocked());
    verifyNoInteractions(revocations);
  }

  @Test
  void guardConflictReloadsAndRecomputes() {
    when(users.readLoginState("user")).thenReturn(state(0, false, -1), state(1, false, 0));
    when(users.updateLoginState(any(), any(), isNull())).thenReturn(false, true);
    bookkeeping.recordFailure("user");
    verify(users, times(2)).readLoginState("user");
    verifyNoInteractions(revocations);
  }

  @Test
  void thresholdRegistersBeforeReloadAndRechecksPolicy() {
    when(users.readLoginState("user")).thenReturn(state(3, false, 0));
    User nowLocked = state(4, true, 0);
    when(users.lockLoginAccount(42L)).thenReturn(nowLocked);
    bookkeeping.recordFailure("user");
    var order = inOrder(revocations, users);
    order.verify(users).readLoginState("user");
    order.verify(revocations).affectUser(42L);
    order.verify(users).lockLoginAccount(42L);
    verify(users, never()).updateLoginState(any(), any(), any());
    assertTrue(nowLocked.isAccountLocked());
    assertEquals(5, nowLocked.getNumConsecutiveLoginFailures());
  }

  @Test
  void exhaustedGuardsFallBackToGateFirst() {
    when(users.readLoginState("user")).thenReturn(state(0, false, -1));
    User current = state(2, false, 0);
    when(users.lockLoginAccount(42L)).thenReturn(current);
    bookkeeping.recordFailure("user");
    verify(users, times(3)).updateLoginState(any(), any(), isNull());
    var order = inOrder(revocations, users);
    order.verify(revocations).affectUser(42L);
    order.verify(users).lockLoginAccount(42L);
    assertEquals(3, current.getNumConsecutiveLoginFailures());
    assertFalse(current.isAccountLocked());
  }
}
