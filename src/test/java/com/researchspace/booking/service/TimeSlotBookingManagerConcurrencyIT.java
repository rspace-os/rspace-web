package com.researchspace.booking.service;

import static com.researchspace.booking.service.TimeSlotBookingManagerIT.sevenDaysFromNow;
import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.booking.config.BookingTimeConfig;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.service.TimeSlotBookingManagerIT.Setup;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingOpeningException;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public class TimeSlotBookingManagerConcurrencyIT extends RealTransactionSpringTestBase {

  private final TimeSlotBookingManager bookingManager;
  private final BookingConfigurationManager configurationManager;
  private final BookingConfigurationDao configurationDao;
  private final InstrumentDao instrumentDao;
  private final JdbcTemplate jdbcTemplate;
  private final FeatureFlagManager featureFlags;
  private final Clock institutionClock;

  @Autowired
  TimeSlotBookingManagerConcurrencyIT(
      TimeSlotBookingManager bookingManager,
      BookingConfigurationManager configurationManager,
      BookingConfigurationDao configurationDao,
      InstrumentDao instrumentDao,
      JdbcTemplate jdbcTemplate,
      FeatureFlagManager featureFlags,
      @Qualifier(BookingTimeConfig.INSTITUTION_CLOCK) Clock institutionClock) {
    this.bookingManager = bookingManager;
    this.configurationManager = configurationManager;
    this.configurationDao = configurationDao;
    this.instrumentDao = instrumentDao;
    this.jdbcTemplate = jdbcTemplate;
    this.featureFlags = featureFlags;
    this.institutionClock = institutionClock;
  }

  private boolean originalBookingBaseline;

  @BeforeEach
  public void enableBooking() {
    User sysadmin = getSysAdminUser();
    originalBookingBaseline =
        featureFlags.getFeatureFlag(BOOKING_ENABLED, sysadmin).orElseThrow().isBaselineValue();
    featureFlags.updateFeatureFlag(
        BOOKING_ENABLED, new FeatureFlagManager.Patch(true, false, null), sysadmin, sysadmin);
  }

  @AfterEach
  public void restoreBookingBaseline() {
    User sysadmin = getSysAdminUser();
    featureFlags.updateFeatureFlag(
        BOOKING_ENABLED,
        new FeatureFlagManager.Patch(originalBookingBaseline, false, null),
        sysadmin,
        sysadmin);
  }

  @Test
  public void configurationLockSerializesTwoOverlappingCreates() throws Exception {
    assertConcurrentOverlappingCreates(false, 1, 1);
  }

  @Test
  public void configurationLockPermitsBothOverlappingCreatesWhenDoubleBookingIsEnabled()
      throws Exception {
    assertConcurrentOverlappingCreates(true, 2, 0);
  }

  @Test
  public void editRejectsOverlapCommittedAfterItsInitialReadSnapshot() throws Exception {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument instrument = createBasicInstrumentForUser(owner, "Edit snapshot scope");
    Setup setup = persistConfiguration(owner, instrument.getId(), false, 0, 0);
    ResolvedBookableTarget target = new ResolvedBookableTarget(setup.target(), setup.instrument());
    Instant start = sevenDaysFromNow();
    var existing =
        bookingManager.createBooking(
            new TimeSlotBookingManager.Create(
                target, Date.from(start), Date.from(start.plus(1, ChronoUnit.HOURS)), null),
            owner,
            owner);
    CountDownLatch snapshotRead = new CountDownLatch(1);
    CountDownLatch competingCreateCommitted = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<Throwable> edit =
          pool.submit(
              () -> {
                try {
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          ignored -> {
                            bookingManager.getBooking(existing.getId(), owner).orElseThrow();
                            snapshotRead.countDown();
                            await(competingCreateCommitted);
                            bookingManager.updateBooking(
                                existing.getId(),
                                new TimeSlotBookingManager.Patch(
                                    Date.from(start.plus(2, ChronoUnit.HOURS)),
                                    Date.from(start.plus(3, ChronoUnit.HOURS)),
                                    false,
                                    null,
                                    null),
                                owner,
                                owner);
                          });
                  return null;
                } catch (RuntimeException exception) {
                  return exception;
                }
              });
      Future<?> create =
          pool.submit(
              () -> {
                await(snapshotRead);
                bookingManager.createBooking(
                    new TimeSlotBookingManager.Create(
                        target,
                        Date.from(start.plus(2, ChronoUnit.HOURS)),
                        Date.from(start.plus(3, ChronoUnit.HOURS)),
                        null),
                    owner,
                    owner);
                competingCreateCommitted.countDown();
              });

      create.get(20, TimeUnit.SECONDS);
      Throwable result = edit.get(20, TimeUnit.SECONDS);
      assertTrue(
          result instanceof BookingOverlapException
              || result instanceof BookingConcurrentModificationException,
          () -> "Unexpected edit result: " + result);
      assertEquals(
          start,
          bookingManager
              .getBooking(existing.getId(), owner)
              .orElseThrow()
              .getStartTime()
              .toInstant());
      assertThrows(
          BookingOverlapException.class,
          () ->
              bookingManager.updateBooking(
                  existing.getId(),
                  new TimeSlotBookingManager.Patch(
                      Date.from(start.plus(2, ChronoUnit.HOURS)),
                      Date.from(start.plus(3, ChronoUnit.HOURS)),
                      false,
                      null,
                      null),
                  owner,
                  owner));
    } finally {
      snapshotRead.countDown();
      competingCreateCommitted.countDown();
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  @Test
  public void archivePreventsAConcurrentFutureCreate() throws Exception {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Archive concurrency scope");
    Setup setup = persistConfiguration(owner, created.getId(), false, 0, 0);
    CountDownLatch archiveLocked = new CountDownLatch(1);
    CountDownLatch createStarted = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);

    try {
      Future<?> archive =
          pool.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          ignored -> {
                            BookingConfiguration locked =
                                configurationDao.lockById(setup.configurationId()).orElseThrow();
                            archiveLocked.countDown();
                            await(createStarted);
                            configurationManager
                                .archiveConfiguration(
                                    setup.configurationId(),
                                    locked.getConfigurationVersion(),
                                    owner,
                                    owner)
                                .orElseThrow();
                          }));
      assertTrue(archiveLocked.await(10, TimeUnit.SECONDS));

      Instant start = sevenDaysFromNow();
      var command =
          new TimeSlotBookingManager.Create(
              new ResolvedBookableTarget(setup.target(), setup.instrument()),
              Date.from(start),
              Date.from(start.plus(1, ChronoUnit.HOURS)),
              "Concurrent archive test");
      Future<Throwable> create =
          pool.submit(
              () -> {
                createStarted.countDown();
                try {
                  bookingManager.createBooking(command, owner, owner);
                  return null;
                } catch (RuntimeException exception) {
                  return exception;
                }
              });

      archive.get(20, TimeUnit.SECONDS);
      assertTrue(create.get(20, TimeUnit.SECONDS) instanceof BookingTargetUnavailableException);
      assertEquals(Integer.valueOf(0), countLiveBookings(setup.configurationId()));
    } finally {
      createStarted.countDown();
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  @Test
  public void archiveCancelsAFutureBookingCreatedAfterItsInitialRead() throws Exception {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Archive create-wins scope");
    Setup setup = persistConfiguration(owner, created.getId(), false, 0, 0);
    ResolvedBookableTarget target = new ResolvedBookableTarget(setup.target(), setup.instrument());
    Instant start = sevenDaysFromNow();
    TimeSlotBooking existing =
        bookingManager.createBooking(
            new TimeSlotBookingManager.Create(
                target, Date.from(start), Date.from(start.plus(1, ChronoUnit.HOURS)), null),
            owner,
            owner);
    CountDownLatch creatorLockHeld = new CountDownLatch(1);
    CountDownLatch archiveRead = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);

    try {
      Future<?> create =
          pool.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          ignored -> {
                            configurationDao.lockById(setup.configurationId()).orElseThrow();
                            creatorLockHeld.countDown();
                            await(archiveRead);
                            bookingManager.createBooking(
                                new TimeSlotBookingManager.Create(
                                    target,
                                    Date.from(start.plus(2, ChronoUnit.HOURS)),
                                    Date.from(start.plus(3, ChronoUnit.HOURS)),
                                    "Created while archive waited"),
                                owner,
                                owner);
                          }));
      assertTrue(creatorLockHeld.await(10, TimeUnit.SECONDS));

      Future<?> archive =
          pool.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          ignored -> {
                            bookingManager.getBooking(existing.getId(), owner).orElseThrow();
                            BookingConfiguration current =
                                configurationDao.getSafeNull(setup.configurationId()).orElseThrow();
                            archiveRead.countDown();
                            configurationManager
                                .archiveConfiguration(
                                    setup.configurationId(),
                                    current.getConfigurationVersion(),
                                    owner,
                                    owner)
                                .orElseThrow();
                          }));

      try {
        archive.get(20, TimeUnit.SECONDS);
      } catch (ExecutionException exception) {
        // MariaDB can reject a stale-snapshot locking read instead of returning current rows.
        assertTrue(exception.getCause() instanceof BookingConcurrentModificationException);
        create.get(20, TimeUnit.SECONDS);
        assertEquals(
            "ACTIVE",
            jdbcTemplate.queryForObject(
                "SELECT state FROM BookingConfiguration WHERE id = ?",
                String.class,
                setup.configurationId()));
        assertEquals(
            Integer.valueOf(2),
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TimeSlotBooking WHERE bookingConfiguration_id = ? AND state ="
                    + " 'CONFIRMED'",
                Integer.class,
                setup.configurationId()));
        configurationManager
            .archiveConfiguration(
                setup.configurationId(),
                jdbcTemplate.queryForObject(
                    "SELECT configurationVersion FROM BookingConfiguration WHERE id = ?",
                    Long.class,
                    setup.configurationId()),
                owner,
                owner)
            .orElseThrow();
      }
      create.get(20, TimeUnit.SECONDS);
      assertEquals(Integer.valueOf(0), countLiveBookings(setup.configurationId()));
    } finally {
      archiveRead.countDown();
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  private void assertConcurrentOverlappingCreates(
      boolean allowDoubleBooking, long expectedSaved, long expectedOverlaps) throws Exception {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Concurrency scope");
    Setup setup = persistConfiguration(owner, created.getId(), allowDoubleBooking, 0, 0);
    CountDownLatch holderLocked = new CountDownLatch(1);
    CountDownLatch releaseHolder = new CountDownLatch(1);
    CountDownLatch contendersStarted = new CountDownLatch(2);
    CountDownLatch startContenders = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(3);

    try {
      Future<?> holder =
          pool.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          ignored -> {
                            configurationDao.lockById(setup.configurationId()).orElseThrow();
                            holderLocked.countDown();
                            await(releaseHolder);
                          }));
      assertTrue(holderLocked.await(10, TimeUnit.SECONDS));

      Instant start =
          LocalDate.ofInstant(institutionClock.instant(), ZoneOffset.UTC)
              .plusDays(7)
              .atTime(10, 0)
              .toInstant(ZoneOffset.UTC);
      var create =
          new TimeSlotBookingManager.Create(
              new ResolvedBookableTarget(setup.target(), setup.instrument()),
              Date.from(start),
              Date.from(start.plus(1, ChronoUnit.HOURS)),
              "Concurrent test");
      Future<Throwable> first =
          submitCreateAttempt(pool, create, owner, contendersStarted, startContenders);
      Future<Throwable> second =
          submitCreateAttempt(pool, create, owner, contendersStarted, startContenders);
      assertTrue(contendersStarted.await(10, TimeUnit.SECONDS));
      startContenders.countDown();
      releaseHolder.countDown();

      List<Throwable> results =
          Arrays.asList(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
      holder.get(20, TimeUnit.SECONDS);

      assertEquals(expectedSaved, results.stream().filter(result -> result == null).count());
      assertEquals(
          expectedOverlaps,
          results.stream().filter(BookingOverlapException.class::isInstance).count());
      assertEquals(
          Integer.valueOf((int) expectedSaved),
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM TimeSlotBooking WHERE bookingConfiguration_id = ? AND deleted ="
                  + " 0",
              Integer.class,
              setup.configurationId()));
    } finally {
      releaseHolder.countDown();
      startContenders.countDown();
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  private int countLiveBookings(Long configurationId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM TimeSlotBooking WHERE bookingConfiguration_id = ? AND state ="
            + " 'CONFIRMED' AND deleted = 0",
        Integer.class,
        configurationId);
  }

  private Future<Throwable> submitCreateAttempt(
      ExecutorService pool,
      TimeSlotBookingManager.Create create,
      User owner,
      CountDownLatch started,
      CountDownLatch start) {
    return pool.submit(() -> attemptCreate(create, owner, started, start));
  }

  private Throwable attemptCreate(
      TimeSlotBookingManager.Create create,
      User owner,
      CountDownLatch started,
      CountDownLatch start) {
    started.countDown();
    await(start);
    try {
      bookingManager.createBooking(create, owner, owner);
      return null;
    } catch (RuntimeException exception) {
      return exception;
    }
  }

  static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(20, TimeUnit.SECONDS));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }

  private Setup persistConfiguration(
      User owner,
      Long instrumentId,
      boolean allowDoubleBooking,
      long bufferBeforeMinutes,
      long bufferAfterMinutes) {
    return TimeSlotBookingManagerIT.persistConfiguration(
        configurationManager,
        instrumentDao,
        this::openTransaction,
        this::commitTransaction,
        owner,
        instrumentId,
        allowDoubleBooking,
        bufferBeforeMinutes,
        bufferAfterMinutes);
  }

  private Setup persistConfiguration(
      User owner,
      Long instrumentId,
      boolean allowDoubleBooking,
      Long slotGranularityMinutes,
      long bufferBeforeMinutes,
      long bufferAfterMinutes) {
    return TimeSlotBookingManagerIT.persistConfiguration(
        configurationManager,
        instrumentDao,
        this::openTransaction,
        this::commitTransaction,
        owner,
        instrumentId,
        allowDoubleBooking,
        slotGranularityMinutes,
        bufferBeforeMinutes,
        bufferAfterMinutes);
  }

  @Test
  public void closingTheWeekdayUnderTheConfigurationLockRejectsAConcurrentCreate()
      throws Exception {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Open days concurrency scope");
    Setup setup = persistConfiguration(owner, created.getId(), false, 0, 0);
    // 2030-01-07 is a Monday in the configuration's UTC scheduling zone.
    var command =
        new TimeSlotBookingManager.Create(
            new ResolvedBookableTarget(setup.target(), setup.instrument()),
            Date.from(Instant.parse("2030-01-07T10:00:00Z")),
            Date.from(Instant.parse("2030-01-07T11:00:00Z")),
            "Concurrent open days test");

    Throwable result =
        writeWhileSettingsChangeHoldsTheLock(
            setup,
            owner,
            new BookingSchedulingSettings.Patch(
                null, null, null, List.of(2, 3, 4, 5, 6, 7), null, null, null, null, null),
            () -> bookingManager.createBooking(command, owner, owner));

    assertTrue(
        result instanceof BookingPolicyException policy
            && policy.reason() == BookingPolicyException.Reason.OPENING_HOURS,
        () -> "Unexpected create result: " + result);
    assertEquals(
        Integer.valueOf(0),
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM TimeSlotBooking WHERE bookingConfiguration_id = ? AND deleted ="
                + " 0",
            Integer.class,
            setup.configurationId()));
  }

  @Test
  public void addingAnExclusiveExceptionUnderTheConfigurationLockRejectsAConcurrentTimeEdit()
      throws Exception {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Exception concurrency scope");
    Setup setup = persistConfiguration(owner, created.getId(), false, 0, 0);
    ResolvedBookableTarget target = new ResolvedBookableTarget(setup.target(), setup.instrument());
    Instant start = Instant.parse("2030-01-07T10:00:00Z");
    var existing =
        bookingManager.createBooking(
            new TimeSlotBookingManager.Create(
                target, Date.from(start), Date.from(start.plus(1, ChronoUnit.HOURS)), null),
            owner,
            owner);
    // Moves the booking to Tuesday 10:00-11:00, which the Tuesday exception excludes.
    var moveToTuesday =
        new TimeSlotBookingManager.Patch(
            Date.from(start.plus(1, ChronoUnit.DAYS)),
            Date.from(start.plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS)),
            false,
            null,
            null);

    Throwable result =
        writeWhileSettingsChangeHoldsTheLock(
            setup,
            owner,
            new BookingSchedulingSettings.Patch(
                null,
                null,
                null,
                null,
                List.of(new BookingOpeningException(2, "12:00", "13:00")),
                null,
                null,
                null,
                null),
            () -> bookingManager.updateBooking(existing.getId(), moveToTuesday, owner, owner));

    assertTrue(
        (result instanceof BookingPolicyException policy
                && policy.reason() == BookingPolicyException.Reason.OPENING_HOURS)
            || result instanceof BookingConcurrentModificationException,
        () -> "Unexpected edit result: " + result);
    assertEquals(
        start,
        bookingManager
            .getBooking(existing.getId(), owner)
            .orElseThrow()
            .getStartTime()
            .toInstant());
    BookingPolicyException retry =
        assertThrows(
            BookingPolicyException.class,
            () -> bookingManager.updateBooking(existing.getId(), moveToTuesday, owner, owner));
    assertEquals(BookingPolicyException.Reason.OPENING_HOURS, retry.reason());
  }

  private Throwable writeWhileSettingsChangeHoldsTheLock(
      Setup setup, User owner, BookingSchedulingSettings.Patch settings, Runnable write)
      throws Exception {
    CountDownLatch settingsLocked = new CountDownLatch(1);
    CountDownLatch writeStarted = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<?> settingsChange =
          pool.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          ignored -> {
                            configurationDao.lockById(setup.configurationId()).orElseThrow();
                            settingsLocked.countDown();
                            await(writeStarted);
                            configurationManager
                                .updateConfiguration(
                                    setup.configurationId(),
                                    new BookingConfigurationManager.Patch(null, null, settings),
                                    owner,
                                    owner)
                                .orElseThrow();
                          }));
      assertTrue(settingsLocked.await(10, TimeUnit.SECONDS));
      Future<Throwable> writeResult =
          pool.submit(
              () -> {
                writeStarted.countDown();
                try {
                  write.run();
                  return null;
                } catch (RuntimeException exception) {
                  return exception;
                }
              });

      settingsChange.get(20, TimeUnit.SECONDS);
      return writeResult.get(20, TimeUnit.SECONDS);
    } finally {
      writeStarted.countDown();
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
  }
}
