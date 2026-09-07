package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ProtectedResourceAccess;
import com.researchspace.service.resourceaccess.ReplaceResourceAccess;
import com.researchspace.service.resourceaccess.ResourceAccessGrant;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public class TimeSlotBookingManagerIT extends RealTransactionSpringTestBase {

  @Autowired private TimeSlotBookingManager bookingManager;
  @Autowired private BookingConfigurationManager configurationManager;
  @Autowired private BookingConfigurationDao configurationDao;
  @Autowired private InstrumentDao instrumentDao;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ResourceAccessManager resourceAccessManager;
  @Autowired private ProtectedResourceAccess<BookingConfiguration, Long> protectedAccess;
  @Autowired private FeatureFlagManager featureFlags;
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
  public void editRejectsAccessRevokedAfterItsInitialReadSnapshot() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument instrument = createBasicInstrumentForUser(owner, "Access snapshot scope");
    Setup setup = persistConfiguration(owner, instrument.getId(), false, 0, 0);
    User booker = createInitAndLoginAnyUser();
    setAudienceRole(setup.configurationId(), owner, "BOOKER");
    Instant start = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    var existing =
        bookingManager.createBooking(
            new TimeSlotBookingManager.Create(
                new ResolvedBookableTarget(setup.target(), setup.instrument()),
                Date.from(start),
                Date.from(start.plus(1, ChronoUnit.HOURS)),
                null),
            booker,
            booker);
    var competing = new TransactionTemplate(getTxMger());
    competing.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    assertThrows(
        BookingConcurrentModificationException.class,
        () ->
            new TransactionTemplate(getTxMger())
                .executeWithoutResult(
                    ignored -> {
                      bookingManager.getBooking(existing.getId(), booker).orElseThrow();
                      competing.executeWithoutResult(
                          other -> setAudienceRole(setup.configurationId(), owner, "NO_ACCESS"));
                      assertTrue(
                          bookingManager
                              .updateBooking(
                                  existing.getId(),
                                  new TimeSlotBookingManager.Patch(
                                      null, null, true, "Revoked edit", null),
                                  booker,
                                  booker)
                              .isEmpty());
                    }));
    assertTrue(
        bookingManager
            .updateBooking(
                existing.getId(),
                new TimeSlotBookingManager.Patch(null, null, true, "Revoked edit", null),
                booker,
                booker)
            .isEmpty());
  }

  private void setAudienceRole(long id, User owner, String role) {
    long version = resourceAccessManager.get(protectedAccess, id, owner).version();
    resourceAccessManager.replace(
        protectedAccess,
        new ReplaceResourceAccess<>(
            id,
            version,
            List.of(
                new ResourceAccessGrant("user:" + owner.getId(), "OWNER"),
                new ResourceAccessGrant("audience:all-users", role))),
        owner,
        owner);
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
  public void editUsesConfigurationChangesCommittedAfterItsInitialReadSnapshot() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument instrument = createBasicInstrumentForUser(owner, "Configuration snapshot scope");
    Setup setup = persistConfiguration(owner, instrument.getId(), false, 0, 0);
    ResolvedBookableTarget target = new ResolvedBookableTarget(setup.target(), setup.instrument());
    Instant start = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    var existing =
        bookingManager.createBooking(
            new TimeSlotBookingManager.Create(
                target, Date.from(start), Date.from(start.plus(1, ChronoUnit.HOURS)), null),
            owner,
            owner);
    var competing = new TransactionTemplate(getTxMger());
    competing.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    assertThrows(
        BookingConcurrentModificationException.class,
        () ->
            new TransactionTemplate(getTxMger())
                .executeWithoutResult(
                    ignored -> {
                      bookingManager.getBooking(existing.getId(), owner).orElseThrow();
                      competing.executeWithoutResult(
                          other ->
                              configurationManager.updateConfiguration(
                                  setup.configurationId(),
                                  new BookingConfigurationManager.Patch(false, null),
                                  owner,
                                  owner));
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
                    }));
  }

  @Test
  public void editRejectsOverlapCommittedAfterItsInitialReadSnapshot() throws Exception {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument instrument = createBasicInstrumentForUser(owner, "Edit snapshot scope");
    Setup setup = persistConfiguration(owner, instrument.getId(), false, 0, 0);
    ResolvedBookableTarget target = new ResolvedBookableTarget(setup.target(), setup.instrument());
    Instant start = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
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

      Instant start = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
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
      assertEquals(
          Integer.valueOf(0),
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM TimeSlotBooking WHERE bookingConfiguration_id = ? AND state ="
                  + " 'CONFIRMED' AND deleted = 0",
              Integer.class,
              setup.configurationId()));
    } finally {
      createStarted.countDown();
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  @Test
  public void asymmetricBuffersRejectCandidatesOnTheCorrectSidesAndKeepExactBoundariesOpen() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Buffer boundary scope");
    Setup setup = persistConfiguration(owner, created.getId(), false, 10, 20);
    ResolvedBookableTarget target = new ResolvedBookableTarget(setup.target(), setup.instrument());

    create(target, owner, "2026-10-17T10:00:00Z", "2026-10-17T11:00:00Z");

    assertThrows(
        BookingOverlapException.class,
        () -> create(target, owner, "2026-10-17T11:15:00Z", "2026-10-17T12:00:00Z"));
    assertThrows(
        BookingOverlapException.class,
        () -> create(target, owner, "2026-10-17T09:00:00Z", "2026-10-17T09:55:00Z"));
    create(target, owner, "2026-10-17T11:20:00Z", "2026-10-17T12:00:00Z");
    create(target, owner, "2026-10-17T09:00:00Z", "2026-10-17T09:50:00Z");
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

      var create =
          new TimeSlotBookingManager.Create(
              new ResolvedBookableTarget(setup.target(), setup.instrument()),
              Date.from(Instant.parse("2026-10-17T10:00:00Z")),
              Date.from(Instant.parse("2026-10-17T11:00:00Z")),
              "Concurrent test");
      Future<Throwable> first =
          pool.submit(() -> attemptCreate(create, owner, contendersStarted, startContenders));
      Future<Throwable> second =
          pool.submit(() -> attemptCreate(create, owner, contendersStarted, startContenders));
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

  private void create(ResolvedBookableTarget target, User owner, String start, String end) {
    bookingManager.createBooking(
        new TimeSlotBookingManager.Create(
            target, Date.from(Instant.parse(start)), Date.from(Instant.parse(end)), null),
        owner,
        owner);
  }

  private Setup persistConfiguration(
      User owner,
      Long instrumentId,
      boolean allowDoubleBooking,
      long bufferBeforeMinutes,
      long bufferAfterMinutes) {
    openTransaction();
    Instrument instrument = instrumentDao.get(instrumentId);
    Hibernate.initialize(instrument.getOwner());
    BookableTargetReference target =
        new BookableTargetReference(BookableTargetType.INSTRUMENT, instrumentId);
    commitTransaction();
    BookingConfiguration configuration =
        configurationManager.createConfiguration(
            new BookingConfigurationManager.Create(
                true,
                "UTC",
                new ResolvedBookableTarget(target, instrument),
                new BookingSchedulingSettings.Patch(
                    null,
                    null,
                    null,
                    bufferBeforeMinutes,
                    bufferAfterMinutes,
                    null,
                    allowDoubleBooking)),
            owner,
            owner);
    return new Setup(configuration.getId(), target, instrument);
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

  private static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(20, TimeUnit.SECONDS));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }

  private record Setup(
      Long configurationId, BookableTargetReference target, Instrument instrument) {}
}
