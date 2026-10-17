package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.UserBookingCalendarSubscriptionDao;
import com.researchspace.booking.service.BookingCalendarManagerImpl.UserSubscriptionConflictException;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public class BookingCalendarManagerIT extends RealTransactionSpringTestBase {

  @Autowired private BookingCalendarManager calendarManager;
  @Autowired private UserBookingCalendarSubscriptionDao subscriptionDao;
  @Autowired private BookingConfigurationDao configurationDao;
  @Autowired private BookingConfigurationManager configurationManager;
  @Autowired private InstrumentDao instrumentDao;
  @Autowired private FeatureFlagManager featureFlags;
  @Autowired private JdbcTemplate jdbcTemplate;

  private boolean originalBookingBaseline;
  private boolean changedBookingBaseline;

  @BeforeEach
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

  @AfterEach
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

  @Test
  public void concurrentItemRotationsFromOneEtagLeaveTheWinningTokenStored() throws Exception {
    User user = createInitAndLoginAnyUser();
    BookingConfiguration configuration = createConfiguration(user);
    BookingCalendarManager.Created initial =
        calendarManager.createOrRotate(configuration.getId(), user, user, "\"inactive\"");

    List<Object> results =
        raceWithHeldLock(
            () -> configurationDao.lockById(configuration.getId()).orElseThrow(),
            () ->
                calendarManager.createOrRotate(
                    configuration.getId(), user, user, initial.status().etag()));

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
    assertStoredItemTokenMatches(configuration.getId(), user.getId(), winner);
  }

  @Test
  public void repeatableReadSnapshotCannotReuseAnEtagAfterAnotherItemRotation() throws Exception {
    User user = createInitAndLoginAnyUser();
    BookingConfiguration configuration = createConfiguration(user);
    BookingCalendarManager.Created initial =
        calendarManager.createOrRotate(configuration.getId(), user, user, "\"inactive\"");
    CountDownLatch snapshotRead = new CountDownLatch(1);
    CountDownLatch winnerCommitted = new CountDownLatch(1);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<Object> staleAttempt =
          pool.submit(
              () -> {
                TransactionTemplate repeatableRead = new TransactionTemplate(getTxMger());
                repeatableRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
                try {
                  return repeatableRead.execute(
                      ignored -> {
                        assertEquals(
                            rawToken(initial),
                            jdbcTemplate.queryForObject(
                                "SELECT rawToken FROM BookableItemCalendarSubscription"
                                    + " WHERE bookingConfiguration_id = ? AND user_id = ?",
                                String.class,
                                configuration.getId(),
                                user.getId()));
                        snapshotRead.countDown();
                        await(winnerCommitted);
                        return calendarManager.createOrRotate(
                            configuration.getId(), user, user, initial.status().etag());
                      });
                } catch (UserSubscriptionConflictException conflict) {
                  return conflict;
                }
              });
      assertTrue(snapshotRead.await(10, TimeUnit.SECONDS));

      BookingCalendarManager.Created winner =
          calendarManager.createOrRotate(
              configuration.getId(), user, user, initial.status().etag());
      winnerCommitted.countDown();

      assertInstanceOf(
          UserSubscriptionConflictException.class, staleAttempt.get(20, TimeUnit.SECONDS));
      assertStoredItemTokenMatches(configuration.getId(), user.getId(), winner);
    } finally {
      winnerCommitted.countDown();
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  @Test
  public void recreatingUserSubscriptionInvalidatesTheOldEtag() {
    User user = createInitAndLoginAnyUser();
    BookingCalendarManager.Created initial =
        calendarManager.createOrRotateUser(user, user, "\"inactive\"");

    calendarManager.revokeUser(user, user);
    BookingCalendarManager.Created replacement =
        calendarManager.createOrRotateUser(user, user, "\"inactive\"");

    assertNotEquals(initial.status().etag(), replacement.status().etag());
    assertThrows(
        UserSubscriptionConflictException.class,
        () -> calendarManager.createOrRotateUser(user, user, initial.status().etag()));
  }

  private List<Object> raceFromLockedUser(User user, String expectedEtag) throws Exception {
    return raceWithHeldLock(
        () -> subscriptionDao.lockUser(user.getId()),
        () -> calendarManager.createOrRotateUser(user, user, expectedEtag));
  }

  private List<Object> raceWithHeldLock(
      Runnable lock, Supplier<BookingCalendarManager.Created> operation) throws Exception {
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
                            lock.run();
                            holderLocked.countDown();
                            await(releaseHolder);
                          }));
      assertTrue(holderLocked.await(10, TimeUnit.SECONDS));
      Future<Object> first =
          pool.submit(() -> attempt(operation, contendersReady, startContenders));
      Future<Object> second =
          pool.submit(() -> attempt(operation, contendersReady, startContenders));
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

  private static Object attempt(
      Supplier<BookingCalendarManager.Created> operation,
      CountDownLatch ready,
      CountDownLatch start) {
    ready.countDown();
    await(start);
    try {
      return operation.get();
    } catch (UserSubscriptionConflictException conflict) {
      return conflict;
    }
  }

  private BookingConfiguration createConfiguration(User owner) {
    ApiInstrument created = createBasicInstrumentForUser(owner, "Calendar concurrency scope");
    openTransaction();
    Instrument instrument = instrumentDao.get(created.getId());
    Hibernate.initialize(instrument.getOwner());
    commitTransaction();
    ResolvedBookableTarget target =
        new ResolvedBookableTarget(
            new BookableTargetReference(BookableTargetType.INSTRUMENT, instrument.getId()),
            instrument);
    return configurationManager.createConfiguration(
        new BookingConfigurationManager.Create(true, "UTC", target), owner, owner);
  }

  private void assertStoredItemTokenMatches(
      Long configurationId, Long userId, BookingCalendarManager.Created winner) {
    Map<String, Object> stored =
        jdbcTemplate.queryForMap(
            "SELECT rawToken, tokenHash FROM BookableItemCalendarSubscription"
                + " WHERE bookingConfiguration_id = ? AND user_id = ?",
            configurationId,
            userId);
    assertEquals(rawToken(winner), stored.get("rawToken"));
    assertEquals(
        winner.status().etag().substring(1, winner.status().etag().length() - 1),
        stored.get("tokenHash"));
  }

  private static String rawToken(BookingCalendarManager.Created created) {
    String query = URI.create(created.subscriptionUrl()).getRawQuery();
    return URLDecoder.decode(query.substring("token=".length()), StandardCharsets.UTF_8);
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
