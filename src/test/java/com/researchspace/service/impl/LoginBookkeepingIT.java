package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.researchspace.booking.dao.BookingCalendarPermissionGateDao;
import com.researchspace.booking.service.BookingCalendarManager;
import com.researchspace.booking.service.BookingCalendarRevocationCoordinator;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import com.researchspace.webapp.filter.DefaultLockoutPolicy;
import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class LoginBookkeepingIT extends RealTransactionSpringTestBase {
  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @AfterEach
  @Override
  public void tearDown() throws Exception {
    super.tearDown();
  }

  private final UserDao users;
  private final BookingCalendarPermissionGateDao gate;
  private final BookingCalendarRevocationCoordinator revocations;
  private final PlatformTransactionManager transactions;

  @Autowired
  LoginBookkeepingIT(
      UserDao users,
      BookingCalendarPermissionGateDao gate,
      BookingCalendarRevocationCoordinator revocations,
      PlatformTransactionManager transactions) {
    this.users = users;
    this.gate = gate;
    this.revocations = revocations;
    this.transactions = transactions;
  }

  private static class Policy extends DefaultLockoutPolicy {
    Policy() {
      setTimeout(10_000);
      setLockoutDuration(10_000);
    }
  }

  private LoginBookkeeping bookkeeping(DefaultLockoutPolicy policy) {
    return new LoginBookkeeping(users, policy, revocations, gate, transactions);
  }

  @Test
  void successfulLoginAndNeutralFailuresFinishWhileSharedGateIsHeld() throws Exception {
    User user = createInitAndLoginAnyUser();
    CountDownLatch sharedHeld = new CountDownLatch(1);
    CountDownLatch releaseShared = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<?> holder =
          pool.submit(
              () ->
                  new TransactionTemplate(transactions)
                      .executeWithoutResult(
                          status -> {
                            gate.lockShared();
                            sharedHeld.countDown();
                            await(releaseShared);
                          }));
      await(sharedHeld);
      Future<?> login =
          pool.submit(
              () -> {
                bookkeeping(new Policy()).recordSuccess(user.getUsername());
                bookkeeping(new Policy()).recordFailure(user.getUsername());
              });
      login.get(10, TimeUnit.SECONDS);
      User saved =
          new TransactionTemplate(transactions)
              .execute(status -> users.readLoginState(user.getUsername()));
      assertEquals(1, saved.getNumConsecutiveLoginFailures());
      assertFalse(saved.isAccountLocked());
      releaseShared.countDown();
      holder.get(10, TimeUnit.SECONDS);
    } finally {
      releaseShared.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void simultaneousThresholdFailuresRecheckUnderGateAndPersistBothFailures() throws Exception {
    User user = createInitAndLoginAnyUser();
    setState(user, 3, false, 0);
    CountDownLatch bothPredicted = new CountDownLatch(2);
    CountDownLatch releasePredictions = new CountDownLatch(1);
    AtomicInteger evaluations = new AtomicInteger();
    Policy policy =
        new Policy() {
          @Override
          public void handleLockoutOnFailure(User state) {
            super.handleLockoutOnFailure(state);
            if (evaluations.incrementAndGet() <= 2) {
              bothPredicted.countDown();
              await(releasePredictions);
            }
          }
        };
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      BookingCalendarRevocationCoordinator counted = Mockito.spy(revocations);
      LoginBookkeeping service = new LoginBookkeeping(users, policy, counted, gate, transactions);
      Future<?> first = pool.submit(() -> service.recordFailure(user.getUsername()));
      Future<?> second = pool.submit(() -> service.recordFailure(user.getUsername()));
      await(bothPredicted);
      releasePredictions.countDown();
      first.get(15, TimeUnit.SECONDS);
      second.get(15, TimeUnit.SECONDS);
      User saved =
          new TransactionTemplate(transactions)
              .execute(status -> users.readLoginState(user.getUsername()));
      assertTrue(saved.isAccountLocked());
      assertEquals(5, saved.getNumConsecutiveLoginFailures());
      assertEquals(4, evaluations.get());
      Mockito.verify(counted, Mockito.times(2)).affectUser(user.getId());
    } finally {
      releasePredictions.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void persistedPolicyMatchesWindowAndExpiryRules() throws Exception {
    User user = createInitAndLoginAnyUser();
    LoginBookkeeping service = bookkeeping(new Policy());
    setState(user, 3, false, 15_000);
    service.recordFailure(user.getUsername());
    User outside = read(user);
    assertFalse(outside.isAccountLocked());
    assertEquals(4, outside.getNumConsecutiveLoginFailures());
    assertTrue(outside.getLoginFailure().getTime() > System.currentTimeMillis() - 2_000);
    setState(user, 4, true, 30_000);
    service.recordFailure(user.getUsername());
    assertFalse(read(user).isAccountLocked());
    assertEquals(1, read(user).getNumConsecutiveLoginFailures());
    setState(user, 4, true, 30_000);
    service.recordSuccess(user.getUsername());
    assertFalse(read(user).isAccountLocked());
    assertEquals(0, read(user).getNumConsecutiveLoginFailures());
    assertNull(read(user).getLoginFailure());
    setState(user, 4, true, 0);
    service.recordFailure(user.getUsername());
    assertTrue(read(user).isAccountLocked());
  }

  @Test
  void newLoginLockWaitsForSharedGateAndRevokesTheItemToken() throws Exception {
    withBookingEnabled(
        () -> {
          User user = createInitAndLoginAnyUser();
          var instrument = createBasicInstrumentForUser(user, "Login lock revocation");
          Long configurationId = bookingConfigurationFor(instrument, user).getId();
          var calendars = applicationContext.getBean(BookingCalendarManager.class);
          calendars.create(configurationId, user, user);
          setState(user, 3, false, 0);
          CountDownLatch held = new CountDownLatch(1);
          CountDownLatch release = new CountDownLatch(1);
          ExecutorService pool = Executors.newFixedThreadPool(2);
          try {
            Future<?> holder =
                pool.submit(
                    () ->
                        new TransactionTemplate(transactions)
                            .executeWithoutResult(
                                status -> {
                                  gate.lockShared();
                                  held.countDown();
                                  await(release);
                                }));
            await(held);
            Future<?> failure =
                pool.submit(() -> bookkeeping(new Policy()).recordFailure(user.getUsername()));
            assertThrows(TimeoutException.class, () -> failure.get(200, TimeUnit.MILLISECONDS));
            assertFalse(read(user).isAccountLocked());
            release.countDown();
            holder.get(15, TimeUnit.SECONDS);
            failure.get(15, TimeUnit.SECONDS);
            assertTrue(read(user).isAccountLocked());
            assertEquals(
                0,
                applicationContext
                    .getBean(JdbcTemplate.class)
                    .queryForObject(
                        "select count(*) from BookableItemCalendarSubscription where"
                            + " bookingConfiguration_id = ? and user_id = ?",
                        Integer.class,
                        configurationId,
                        user.getId()));
          } finally {
            release.countDown();
            pool.shutdownNow();
          }
        });
  }

  @Test
  void concurrentNeutralFailuresRetryTheGuardWithoutRegistering() throws Exception {
    User user = createInitAndLoginAnyUser();
    CountDownLatch predicted = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger attempts = new AtomicInteger();
    Policy policy =
        new Policy() {
          @Override
          public void handleLockoutOnFailure(User state) {
            super.handleLockoutOnFailure(state);
            if (attempts.incrementAndGet() <= 2) {
              predicted.countDown();
              await(release);
            }
          }
        };
    BookingCalendarRevocationCoordinator counted = Mockito.spy(revocations);
    LoginBookkeeping service = new LoginBookkeeping(users, policy, counted, gate, transactions);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<?> first = pool.submit(() -> service.recordFailure(user.getUsername()));
      Future<?> second = pool.submit(() -> service.recordFailure(user.getUsername()));
      await(predicted);
      release.countDown();
      first.get(15, TimeUnit.SECONDS);
      second.get(15, TimeUnit.SECONDS);
      assertEquals(2, read(user).getNumConsecutiveLoginFailures());
      assertEquals(3, attempts.get());
      Mockito.verifyNoInteractions(counted);
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void publicLoginEntrypointsUseOneDatabaseConnection() throws Exception {
    User user = createInitAndLoginAnyUser();
    BasicDataSource pool = dataSource.unwrap(BasicDataSource.class);
    AtomicInteger maximumConnections = new AtomicInteger();
    Policy tapped =
        new Policy() {
          @Override
          public void handleLockoutOnSuccess(User state) {
            maximumConnections.accumulateAndGet(pool.getNumActive(), Math::max);
            super.handleLockoutOnSuccess(state);
          }

          @Override
          public void handleLockoutOnFailure(User state) {
            maximumConnections.accumulateAndGet(pool.getNumActive(), Math::max);
            super.handleLockoutOnFailure(state);
          }
        };
    LoginBookkeeping original = applicationContext.getBean(LoginBookkeeping.class);
    ReflectionTestUtils.setField(
        userMgr,
        "loginBookkeeping",
        new LoginBookkeeping(users, tapped, revocations, gate, transactions));
    try {
      userMgr.recordLoginSuccess(user);
      userMgr.recordLoginFailure(user.getUsername());
      assertEquals(1, maximumConnections.get());
      assertEquals(1, read(user).getNumConsecutiveLoginFailures());
    } finally {
      ReflectionTestUtils.setField(userMgr, "loginBookkeeping", original);
    }
  }

  private User read(User user) {
    return new TransactionTemplate(transactions)
        .execute(status -> users.readLoginState(user.getUsername()));
  }

  private void setState(User user, int count, boolean locked, long age) {
    User fresh = userMgr.get(user.getId());
    fresh.setNumConsecutiveLoginFailures((byte) count);
    fresh.setAccountLocked(locked);
    fresh.setLoginFailure(new Date(System.currentTimeMillis() - age));
    userMgr.save(fresh);
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(15, TimeUnit.SECONDS));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }
}
