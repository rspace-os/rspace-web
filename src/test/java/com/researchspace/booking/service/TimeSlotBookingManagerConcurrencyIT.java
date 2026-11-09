package com.researchspace.booking.service;

import static com.researchspace.booking.service.TimeSlotBookingManagerIT.sevenDaysFromNow;
import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static com.researchspace.testutils.BookingTestSchedules.hours;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.booking.config.BookingTimeConfig;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.service.TimeSlotBookingManagerIT.Setup;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingOpeningHoursEntry;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import jakarta.persistence.LockModeType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionImplementor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.hibernate5.SessionHolder;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void createSeesConfigurationChangeAfterItsCachedRead(boolean disable) throws Exception {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Current configuration");
    Setup setup = persistConfiguration(owner, created.getId(), true, 0, 0);
    Instant start = sevenDaysFromNow();
    var command =
        new TimeSlotBookingManager.Create(
            new ResolvedBookableTarget(setup.target(), setup.instrument()),
            Date.from(start),
            Date.from(start.plus(1, ChronoUnit.HOURS)),
            null);
    if (!disable) bookingManager.createBooking(command, owner, owner);
    CountDownLatch changed = new CountDownLatch(1);
    CountDownLatch cached = new CountDownLatch(1);
    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      Future<?> change =
          workers.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          ignored -> {
                            var session = sessionFactory.getCurrentSession();
                            BookingConfiguration configuration =
                                session
                                    .createQuery(
                                        "from BookingConfiguration where id = :id",
                                        BookingConfiguration.class)
                                    .setParameter("id", setup.configurationId())
                                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                                    .getSingleResult();
                            if (disable) configuration.setEnabled(false);
                            else configuration.setAllowDoubleBooking(false);
                            session.flush();
                            changed.countDown();
                            await(cached);
                          }));
      assertTrue(changed.await(20, TimeUnit.SECONDS));
      Future<Throwable> create =
          workers.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .execute(
                          ignored -> {
                            configurationDao.get(setup.configurationId());
                            cached.countDown();
                            try {
                              bookingManager.createBooking(command, owner, owner);
                              return null;
                            } catch (RuntimeException failure) {
                              ignored.setRollbackOnly();
                              return failure;
                            }
                          }));
      change.get(20, TimeUnit.SECONDS);
      Throwable failure = create.get(20, TimeUnit.SECONDS);
      if (disable) {
        assertInstanceOf(BookingTargetUnavailableException.class, failure);
      } else {
        assertInstanceOf(BookingOverlapException.class, failure);
      }
    } finally {
      cached.countDown();
      workers.shutdownNow();
      workers.awaitTermination(20, TimeUnit.SECONDS);
    }
  }

  @Test
  void concurrentConfigurationSavesRejectTheCachedVersion() throws Exception {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Concurrent configuration saves");
    Setup setup = persistConfiguration(owner, created.getId(), false, 0, 0);
    long version =
        configurationManager
            .getConfiguration(setup.configurationId(), owner)
            .orElseThrow()
            .getConfigurationVersion();
    CountDownLatch firstSaved = new CountDownLatch(1);
    CountDownLatch secondCached = new CountDownLatch(1);
    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      Future<?> first =
          workers.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          ignored -> {
                            configurationManager
                                .updateConfiguration(
                                    setup.configurationId(),
                                    new BookingConfigurationManager.Patch(false, null),
                                    version,
                                    owner,
                                    owner)
                                .orElseThrow();
                            firstSaved.countDown();
                            await(secondCached);
                          }));
      assertTrue(firstSaved.await(20, TimeUnit.SECONDS));
      Future<?> second =
          workers.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          ignored -> {
                            configurationDao.get(setup.configurationId());
                            secondCached.countDown();
                            configurationManager
                                .updateConfiguration(
                                    setup.configurationId(),
                                    new BookingConfigurationManager.Patch(null, "Europe/Berlin"),
                                    version,
                                    owner,
                                    owner)
                                .orElseThrow();
                          }));
      first.get(20, TimeUnit.SECONDS);
      ExecutionException rejected =
          assertThrows(ExecutionException.class, () -> second.get(20, TimeUnit.SECONDS));
      assertInstanceOf(BookingConcurrentModificationException.class, rejected.getCause());
      BookingConfiguration saved =
          configurationManager.getConfiguration(setup.configurationId(), owner).orElseThrow();
      assertFalse(saved.isEnabled());
      assertEquals("UTC", saved.getTimeZone());
      assertEquals(version + 1, saved.getConfigurationVersion());
    } finally {
      secondCached.countDown();
      workers.shutdownNow();
      assertTrue(workers.awaitTermination(20, TimeUnit.SECONDS));
    }
  }

  @Test
  void startPassingWhileTheConfigurationLockIsHeldRejectsTheBooking() throws Exception {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Past-start lock wait");
    Setup setup = persistConfiguration(owner, created.getId(), false, 0, 0);
    Instant start = sevenDaysFromNow();
    var now = new AtomicReference<>(start.minusSeconds(1));
    CountDownLatch validated = new CountDownLatch(1);
    CountDownLatch locked = new CountDownLatch(1);
    Clock advancing = mock(Clock.class);
    when(advancing.instant())
        .thenAnswer(
            ignored -> {
              Instant result = now.get();
              validated.countDown();
              return result;
            });
    Object managerTarget = AopTestUtils.getUltimateTargetObject(bookingManager);
    Clock original = (Clock) ReflectionTestUtils.getField(managerTarget, "clock");
    ReflectionTestUtils.setField(managerTarget, "clock", advancing);
    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      Future<?> holder =
          workers.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          ignored -> {
                            configurationDao.lockById(setup.configurationId()).orElseThrow();
                            locked.countDown();
                            await(validated);
                            now.set(start);
                          }));
      assertTrue(locked.await(20, TimeUnit.SECONDS));
      Future<?> create =
          workers.submit(
              () ->
                  bookingManager.createBooking(
                      new TimeSlotBookingManager.Create(
                          new ResolvedBookableTarget(setup.target(), setup.instrument()),
                          Date.from(start),
                          Date.from(start.plus(1, ChronoUnit.HOURS)),
                          null),
                      owner,
                      owner));
      holder.get(20, TimeUnit.SECONDS);
      var failure = assertThrows(ExecutionException.class, () -> create.get(20, TimeUnit.SECONDS));
      assertInstanceOf(BookingStartInPastException.class, failure.getCause());
      assertEquals(Integer.valueOf(0), countLiveBookings(setup.configurationId()));
    } finally {
      validated.countDown();
      workers.shutdownNow();
      workers.awaitTermination(20, TimeUnit.SECONDS);
      ReflectionTestUtils.setField(managerTarget, "clock", original);
    }
  }

  @Test
  void configurationLockDoesNotLockItsAuditActor() throws Exception {
    User owner = createInitAndLoginAnyUser();
    User actor = getSysAdminUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Configuration actor lock");
    var instrument =
        new TransactionTemplate(getTxMger()).execute(ignored -> instrumentDao.get(created.getId()));
    var target = new BookableTargetReference(BookableTargetType.INSTRUMENT, created.getId());
    BookingConfiguration configuration =
        configurationManager.createConfiguration(
            new BookingConfigurationManager.Create(
                true, "UTC", new ResolvedBookableTarget(target, instrument)),
            owner,
            actor);
    var inspectedSql = new CopyOnWriteArrayList<String>();
    Object daoTarget = AopTestUtils.getUltimateTargetObject(configurationDao);
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      Future<?> holder =
          workers.submit(
              () -> {
                try (var session =
                    sessionFactory
                        .withOptions()
                        .statementInspector(
                            sql -> {
                              inspectedSql.add(sql);
                              return sql;
                            })
                        .openSession()) {
                  var transaction = session.beginTransaction();
                  TransactionSynchronizationManager.bindResource(
                      sessionFactory, new SessionHolder(session));
                  try {
                    var cached = session.get(BookingConfiguration.class, configuration.getId());
                    ReflectionTestUtils.invokeMethod(daoTarget, "refreshSchedulingState", cached);
                    locked.countDown();
                    await(release);
                    transaction.commit();
                  } finally {
                    TransactionSynchronizationManager.unbindResource(sessionFactory);
                    if (transaction.isActive()) transaction.rollback();
                  }
                }
              });
      assertTrue(locked.await(20, TimeUnit.SECONDS));
      var lockingSql =
          inspectedSql.stream()
              .map(sql -> sql.toLowerCase(Locale.ROOT).replace("`", ""))
              .filter(sql -> sql.contains(" for update"))
              .toList();
      assertEquals(1, lockingSql.size());
      assertFalse(
          lockingSql.get(0).matches("(?s).*\\b(join|from)\\s+user\\b.*"), lockingSql.get(0));
      Future<Long> userLock =
          workers.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .execute(
                          ignored ->
                              sessionFactory
                                  .getCurrentSession()
                                  .createNativeQuery(
                                      "select id from User where id = :id for update", Long.class)
                                  .setParameter("id", actor.getId())
                                  .getSingleResult()));
      assertEquals(actor.getId(), userLock.get(5, TimeUnit.SECONDS));
      release.countDown();
      holder.get(20, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      workers.shutdownNow();
      workers.awaitTermination(20, TimeUnit.SECONDS);
    }
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
  void bulkArchiveWaitsForCreateTargetsBeforeLockingConfigurations() throws Exception {
    User owner = createInitAndLoginAnyUser();
    User admin = getSysAdminUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Bulk archive lock order");
    Setup setup = persistConfiguration(owner, created.getId(), false, 0, 0);
    var target = new ResolvedBookableTarget(setup.target(), setup.instrument());
    Instant start = sevenDaysFromNow();
    var existing =
        bookingManager.createBooking(
            new TimeSlotBookingManager.Create(
                target, Date.from(start), Date.from(start.plus(1, ChronoUnit.HOURS)), null),
            owner,
            owner);
    var daoTarget = AopTestUtils.getUltimateTargetObject(configurationDao);
    var originalInstruments =
        (InstrumentDao) ReflectionTestUtils.getField(daoTarget, "instruments");
    var originalFactory =
        (SessionFactory) ReflectionTestUtils.getField(daoTarget, "sessionFactory");
    var creator = new AtomicReference<Thread>();
    var archiver = new AtomicReference<Thread>();
    CountDownLatch createTargetsLocked = new CountDownLatch(1);
    CountDownLatch letCreateContinue = new CountDownLatch(1);
    CountDownLatch bulkRequestsTargetLock = new CountDownLatch(1);
    var instrumentSpy =
        spy((InstrumentDao) AopTestUtils.getUltimateTargetObject(originalInstruments));
    doAnswer(
            call -> {
              Object locked = call.callRealMethod();
              if (Thread.currentThread() == creator.get()) {
                createTargetsLocked.countDown();
                await(letCreateContinue);
              }
              return locked;
            })
        .when(instrumentSpy)
        .lockById(created.getId());
    var observedFactory = mock(SessionFactory.class);
    when(observedFactory.getCurrentSession())
        .thenAnswer(
            call -> {
              var actual = originalFactory.getCurrentSession();
              if (Thread.currentThread() != archiver.get()) return actual;
              var observed =
                  (Session)
                      Proxy.newProxyInstance(
                          SessionImplementor.class.getClassLoader(),
                          new Class<?>[] {SessionImplementor.class},
                          (proxy, method, args) -> {
                            if (method.getName().equals("createNativeQuery")
                                && args.length == 2
                                && args[0] instanceof String sql
                                && args[1] == Long.class
                                && sql.startsWith("select id from InstrumentEntity")
                                && sql.contains("for update")) bulkRequestsTargetLock.countDown();
                            try {
                              return method.invoke(actual, args);
                            } catch (InvocationTargetException failure) {
                              throw failure.getCause();
                            }
                          });
              return observed;
            });
    ReflectionTestUtils.setField(daoTarget, "instruments", instrumentSpy);
    ReflectionTestUtils.setField(daoTarget, "sessionFactory", observedFactory);
    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      Future<TimeSlotBooking> create =
          workers.submit(
              () -> {
                creator.set(Thread.currentThread());
                return bookingManager.createBooking(
                    new TimeSlotBookingManager.Create(
                        target,
                        Date.from(start.plus(2, ChronoUnit.HOURS)),
                        Date.from(start.plus(3, ChronoUnit.HOURS)),
                        null),
                    owner,
                    owner);
              });
      assertTrue(createTargetsLocked.await(20, TimeUnit.SECONDS));
      Future<?> archive =
          workers.submit(
              () -> {
                archiver.set(Thread.currentThread());
                var request =
                    new ResourceRequest(
                        new FilterExpression.Comparison(
                            "id", Operator.EQUAL, List.of(setup.configurationId()), false),
                        List.of(),
                        new ResourceRequest.Page(1, 100),
                        FieldSelection.all(),
                        IncludeTree.empty());
                return configurationManager.archiveConfigurations(request, admin, admin);
              });
      if (!bulkRequestsTargetLock.await(20, TimeUnit.SECONDS)) {
        if (archive.isDone()) archive.get();
        fail("Archive thread: " + Arrays.toString(archiver.get().getStackTrace()));
      }
      // The creator owns the target. Bulk archive must wait there and leave the configuration free.
      new TransactionTemplate(getTxMger())
          .executeWithoutResult(
              ignored ->
                  sessionFactory
                      .getCurrentSession()
                      .createNativeQuery(
                          "select id from BookingConfiguration where id = :id for update nowait",
                          Long.class)
                      .setParameter("id", setup.configurationId())
                      .getSingleResult());
      letCreateContinue.countDown();
      var saved = create.get(20, TimeUnit.SECONDS);
      archive.get(20, TimeUnit.SECONDS);
      new TransactionTemplate(getTxMger())
          .executeWithoutResult(
              ignored -> {
                for (Long id : List.of(existing.getId(), saved.getId()))
                  assertEquals(
                      BookingState.CANCELLED,
                      sessionFactory.getCurrentSession().get(TimeSlotBooking.class, id).getState());
              });
    } finally {
      letCreateContinue.countDown();
      workers.shutdownNow();
      workers.awaitTermination(30, TimeUnit.SECONDS);
      ReflectionTestUtils.setField(daoTarget, "instruments", originalInstruments);
      ReflectionTestUtils.setField(daoTarget, "sessionFactory", originalFactory);
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

  @ParameterizedTest(name = "{0}")
  @MethodSource("openingHoursChangeScenarios")
  public void openingHoursChangesUnderConfigurationLockRejectInvalidWrites(
      String scenario, boolean timeEdit, BookingSchedulingSettings.Patch settings)
      throws Exception {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Opening hours concurrency scope");
    Setup setup = persistConfiguration(owner, created.getId(), false, 0, 0);
    ResolvedBookableTarget target = new ResolvedBookableTarget(setup.target(), setup.instrument());
    Instant start = Instant.parse("2030-01-07T10:00:00Z");
    TimeSlotBooking existing =
        timeEdit
            ? bookingManager.createBooking(
                new TimeSlotBookingManager.Create(
                    target, Date.from(start), Date.from(start.plus(1, ChronoUnit.HOURS)), null),
                owner,
                owner)
            : null;
    TimeSlotBookingManager.Patch moveToTuesday =
        new TimeSlotBookingManager.Patch(
            Date.from(start.plus(1, ChronoUnit.DAYS)),
            Date.from(start.plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS)),
            false,
            null,
            null);
    Runnable write =
        timeEdit
            ? () -> bookingManager.updateBooking(existing.getId(), moveToTuesday, owner, owner)
            : () ->
                bookingManager.createBooking(
                    new TimeSlotBookingManager.Create(
                        target,
                        Date.from(start),
                        Date.from(start.plus(1, ChronoUnit.HOURS)),
                        "Concurrent open-hours test"),
                    owner,
                    owner);

    Throwable result = writeWhileSettingsChangeHoldsTheLock(setup, owner, settings, write);

    if (timeEdit) {
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
    } else {
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
  }

  private static Stream<Arguments> openingHoursChangeScenarios() {
    return Stream.of(
        Arguments.of(
            "closing Monday rejects a concurrent create",
            false,
            new BookingSchedulingSettings.Patch(
                null, hours("00:00", "24:00", 2, 3, 4, 5, 6, 7), null, null, null, null)),
        Arguments.of(
            "Tuesday exception rejects a concurrent time edit",
            true,
            new BookingSchedulingSettings.Patch(
                null, hoursWithTuesdayException(), null, null, null, null)));
  }

  private static List<BookingOpeningHoursEntry> hoursWithTuesdayException() {
    return Stream.of(
            hours("00:00", "24:00", 1),
            List.of(new BookingOpeningHoursEntry(2, "12:00", "13:00")),
            hours("00:00", "24:00", 3, 4, 5, 6, 7))
        .flatMap(List::stream)
        .toList();
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
