package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.booking.dao.UserBookingCalendarSubscriptionDao;
import com.researchspace.booking.service.BookingCalendarManagerImpl.UserSubscriptionConflictException;
import com.researchspace.model.User;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public class BookingCalendarManagerIT extends RealTransactionSpringTestBase {

  @Autowired private BookingCalendarManager calendarManager;
  @Autowired private UserBookingCalendarSubscriptionDao subscriptionDao;
  @Autowired private FeatureFlagManager featureFlags;
  @Autowired private JdbcTemplate jdbcTemplate;

  private boolean originalBookingBaseline;
  private boolean changedBookingBaseline;

  @Before
  public void enableBooking() {
    User sysadmin = getSysAdminUser();
    originalBookingBaseline =
        featureFlags.getFeatureFlag(BOOKING_ENABLED, sysadmin).orElseThrow().isBaselineValue();
    changedBookingBaseline = !featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, sysadmin);
    if (changedBookingBaseline) {
      featureFlags
          .updateFeatureFlag(
              BOOKING_ENABLED, new FeatureFlagManager.Patch(true, false, null), sysadmin, sysadmin)
          .orElseThrow();
    }
  }

  @After
  public void restoreBookingBaseline() {
    if (!changedBookingBaseline) return;
    User sysadmin = getSysAdminUser();
    featureFlags.updateFeatureFlag(
        BOOKING_ENABLED,
        new FeatureFlagManager.Patch(originalBookingBaseline, false, null),
        sysadmin,
        sysadmin);
  }

  @Test
  public void concurrentInactiveCreatesLeaveOneSubscriptionAndOneConflict() throws Exception {
    User user = createInitAndLoginAnyUser();

    List<Object> results = raceFromLockedUser(user, "\"inactive\"");

    assertEquals(
        1, results.stream().filter(BookingCalendarManager.Created.class::isInstance).count());
    assertEquals(
        1, results.stream().filter(UserSubscriptionConflictException.class::isInstance).count());
    assertEquals(
        Integer.valueOf(1),
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM UserBookingCalendarSubscription WHERE user_id = ?",
            Integer.class,
            user.getId()));
    BookingCalendarManager.Status current = calendarManager.userStatus(user, user);
    BookingCalendarManager.Created winner =
        (BookingCalendarManager.Created)
            results.stream()
                .filter(BookingCalendarManager.Created.class::isInstance)
                .findFirst()
                .orElseThrow();
    assertEquals(winner.status().etag(), current.etag());
    assertEquals(winner.subscriptionUrl(), current.subscriptionUrl());
  }

  @Test
  public void concurrentRotationsFromOneVersionReturnOneNewVersionAndOneConflict()
      throws Exception {
    User user = createInitAndLoginAnyUser();
    BookingCalendarManager.Created initial =
        calendarManager.createOrRotateUser(user, user, "\"inactive\"");

    List<Object> results = raceFromLockedUser(user, initial.status().etag());

    assertEquals(
        1, results.stream().filter(BookingCalendarManager.Created.class::isInstance).count());
    assertEquals(
        1, results.stream().filter(UserSubscriptionConflictException.class::isInstance).count());
    BookingCalendarManager.Created winner =
        (BookingCalendarManager.Created)
            results.stream()
                .filter(BookingCalendarManager.Created.class::isInstance)
                .findFirst()
                .orElseThrow();
    assertNotEquals(initial.status().etag(), winner.status().etag());
    assertNotEquals(initial.subscriptionUrl(), winner.subscriptionUrl());
    assertEquals(winner.status().etag(), calendarManager.userStatus(user, user).etag());
  }

  private List<Object> raceFromLockedUser(User user, String expectedEtag) throws Exception {
    CountDownLatch holderLocked = new CountDownLatch(1);
    CountDownLatch releaseHolder = new CountDownLatch(1);
    CountDownLatch contendersReady = new CountDownLatch(2);
    CountDownLatch startContenders = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(3);
    try {
      Future<?> holder =
          pool.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          ignored -> {
                            subscriptionDao.lockUser(user.getId());
                            holderLocked.countDown();
                            await(releaseHolder);
                          }));
      assertTrue(holderLocked.await(10, TimeUnit.SECONDS));
      Future<Object> first =
          pool.submit(() -> attemptRotation(user, expectedEtag, contendersReady, startContenders));
      Future<Object> second =
          pool.submit(() -> attemptRotation(user, expectedEtag, contendersReady, startContenders));
      assertTrue(contendersReady.await(10, TimeUnit.SECONDS));
      startContenders.countDown();
      releaseHolder.countDown();

      List<Object> results =
          Arrays.asList(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
      holder.get(20, TimeUnit.SECONDS);
      return results;
    } finally {
      releaseHolder.countDown();
      startContenders.countDown();
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  private Object attemptRotation(
      User user, String expectedEtag, CountDownLatch ready, CountDownLatch start) {
    ready.countDown();
    await(start);
    try {
      return calendarManager.createOrRotateUser(user, user, expectedEtag);
    } catch (UserSubscriptionConflictException conflict) {
      return conflict;
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(20, TimeUnit.SECONDS));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }
}
