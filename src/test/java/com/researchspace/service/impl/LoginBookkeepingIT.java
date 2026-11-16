package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import com.researchspace.webapp.filter.DefaultLockoutPolicy;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class LoginBookkeepingIT extends RealTransactionSpringTestBase {
  private @Autowired UserDao users;
  private @Autowired PlatformTransactionManager transactions;

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

  private static class Policy extends DefaultLockoutPolicy {
    Policy() {
      setTimeout(10_000);
      setLockoutDuration(10_000);
    }
  }

  /** Blocks the first two policy evaluations until both attempts have read the same state. */
  private static class RacingPolicy extends Policy {
    final AtomicInteger evaluations = new AtomicInteger();
    final CountDownLatch bothRead = new CountDownLatch(2);
    final CountDownLatch release = new CountDownLatch(1);

    @Override
    public void handleLockoutOnFailure(User state) {
      super.handleLockoutOnFailure(state);
      if (evaluations.incrementAndGet() <= 2) {
        bothRead.countDown();
        await(release);
      }
    }
  }

  @Test
  void persistedPolicyMatchesWindowAndExpiryRules() {
    User user = createInitAndLoginAnyUser();
    LoginBookkeeping service = new LoginBookkeeping(users, new Policy(), transactions);
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

    setState(user, 3, false, 0);
    service.recordFailure(user.getUsername());
    assertTrue(read(user).isAccountLocked());
  }

  @Test
  void concurrentFailuresAreBothCounted() throws Exception {
    User user = createInitAndLoginAnyUser();
    RacingPolicy policy = new RacingPolicy();
    assertEquals(2, raceFailures(user, policy).getNumConsecutiveLoginFailures());
    assertEquals(3, policy.evaluations.get());
  }

  @Test
  void concurrentThresholdFailuresLockAndAreBothCounted() throws Exception {
    User user = createInitAndLoginAnyUser();
    setState(user, 3, false, 0);
    RacingPolicy policy = new RacingPolicy();
    User saved = raceFailures(user, policy);
    assertTrue(saved.isAccountLocked());
    assertEquals(5, saved.getNumConsecutiveLoginFailures());
    assertEquals(3, policy.evaluations.get());
  }

  @Test
  void successfulLoginKeepsConcurrentEditsToTheAccount() {
    User user = createInitAndLoginAnyUser();
    User stale = userMgr.get(user.getId());
    User edited = userMgr.get(user.getId());
    edited.setFirstName("Edited");
    userMgr.save(edited);

    userMgr.recordLoginSuccess(stale);

    User saved = userMgr.get(user.getId());
    assertEquals("Edited", saved.getFirstName());
    assertNotNull(saved.getLastLogin());
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
        userMgr, "loginBookkeeping", new LoginBookkeeping(users, tapped, transactions));
    try {
      userMgr.recordLoginSuccess(user);
      userMgr.recordLoginFailure(user.getUsername());
      assertEquals(1, maximumConnections.get());
      assertEquals(1, read(user).getNumConsecutiveLoginFailures());
    } finally {
      ReflectionTestUtils.setField(userMgr, "loginBookkeeping", original);
    }
  }

  private User raceFailures(User user, RacingPolicy policy) throws Exception {
    LoginBookkeeping service = new LoginBookkeeping(users, policy, transactions);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<?> first = pool.submit(() -> service.recordFailure(user.getUsername()));
      Future<?> second = pool.submit(() -> service.recordFailure(user.getUsername()));
      await(policy.bothRead);
      policy.release.countDown();
      first.get(15, TimeUnit.SECONDS);
      second.get(15, TimeUnit.SECONDS);
      return read(user);
    } finally {
      policy.release.countDown();
      pool.shutdownNow();
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
