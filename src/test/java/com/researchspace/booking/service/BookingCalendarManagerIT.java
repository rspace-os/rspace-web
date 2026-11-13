package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static com.researchspace.testutils.BookingResourceTestFixtures.useSnapshotIsolation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventorySharingMode;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.UserBookingCalendarSubscriptionDao;
import com.researchspace.booking.service.BookingCalendarManagerImpl.UserSubscriptionConflictException;
import com.researchspace.core.util.CryptoUtils;
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
import java.sql.SQLException;
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

  private final BookingCalendarManager calendarManager;
  private final UserBookingCalendarSubscriptionDao subscriptionDao;
  private final BookingConfigurationDao configurationDao;
  private final BookingConfigurationManager configurationManager;
  private final InstrumentDao instrumentDao;
  private final FeatureFlagManager featureFlags;
  private final JdbcTemplate jdbcTemplate;

  @Autowired
  BookingCalendarManagerIT(
      BookingCalendarManager calendarManager,
      UserBookingCalendarSubscriptionDao subscriptionDao,
      BookingConfigurationDao configurationDao,
      BookingConfigurationManager configurationManager,
      InstrumentDao instrumentDao,
      FeatureFlagManager featureFlags,
      JdbcTemplate jdbcTemplate) {
    this.calendarManager = calendarManager;
    this.subscriptionDao = subscriptionDao;
    this.configurationDao = configurationDao;
    this.configurationManager = configurationManager;
    this.instrumentDao = instrumentDao;
    this.featureFlags = featureFlags;
    this.jdbcTemplate = jdbcTemplate;
  }

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
  public void oldSnapshotConcurrentIssueRollsBackTheWholeSourceTransactionOnMariaDb1020() {
    User owner = createInitAndLoginAnyUser();
    ItemSetup setup = itemConfiguration(owner, "Post-snapshot token issue");
    TransactionTemplate competing = requiresNew();

    BookingCalendarManager.Created[] committed = new BookingCalendarManager.Created[1];
    RuntimeException failure =
        assertThrows(
            RuntimeException.class,
            () ->
                new TransactionTemplate(getTxMger())
                    .execute(
                        ignored -> {
                          useSnapshotIsolation(jdbcTemplate, true);
                          ApiInstrument permissionSource =
                              instrumentApiMgr.getApiInstrumentById(setup.instrumentId(), owner);
                          jdbcTemplate.queryForObject(
                              "SELECT COUNT(*) FROM BookableItemCalendarSubscription",
                              Integer.class);
                          committed[0] =
                              competing.execute(
                                  other ->
                                      calendarManager.createOrRotate(
                                          setup.configurationId(), owner, owner, "\"inactive\""));
                          permissionSource.setSharingMode(ApiInventorySharingMode.OWNER_ONLY);
                          instrumentApiMgr.updateApiInstrument(permissionSource, owner);
                          return calendarManager.createOrRotate(
                              setup.configurationId(), owner, owner, "\"inactive\"");
                        }));

    assertMariaDbConcurrentReadRestart(failure);
    BookingCalendarManager.Status current =
        calendarManager.status(setup.configurationId(), owner, owner);
    assertEquals(committed[0].status().etag(), current.etag());
    assertEquals(committed[0].subscriptionUrl(), current.subscriptionUrl());
    assertEquals(
        ApiInventorySharingMode.OWNER_GROUPS,
        instrumentApiMgr.getApiInstrumentById(setup.instrumentId(), owner).getSharingMode());
  }

  @Test
  public void preinitializedTokenConcurrentRotationRollsBackTheSourceTransactionOnMariaDb1020() {
    User owner = createInitAndLoginAnyUser();
    ItemSetup setup = itemConfiguration(owner, "Post-snapshot token rotation");
    BookingCalendarManager.Created original =
        calendarManager.createOrRotate(setup.configurationId(), owner, owner, "\"inactive\"");
    TransactionTemplate competing = requiresNew();

    BookingCalendarManager.Created[] rotated = new BookingCalendarManager.Created[1];
    RuntimeException failure =
        assertThrows(
            RuntimeException.class,
            () ->
                new TransactionTemplate(getTxMger())
                    .execute(
                        ignored -> {
                          useSnapshotIsolation(jdbcTemplate, true);
                          ApiInstrument permissionSource =
                              instrumentApiMgr.getApiInstrumentById(setup.instrumentId(), owner);
                          assertEquals(
                              original.status().etag(),
                              calendarManager.status(setup.configurationId(), owner, owner).etag());
                          rotated[0] =
                              competing.execute(
                                  other ->
                                      calendarManager.createOrRotate(
                                          setup.configurationId(),
                                          owner,
                                          owner,
                                          original.status().etag()));
                          permissionSource.setSharingMode(ApiInventorySharingMode.OWNER_ONLY);
                          instrumentApiMgr.updateApiInstrument(permissionSource, owner);
                          return calendarManager.createOrRotate(
                              setup.configurationId(), owner, owner, original.status().etag());
                        }));

    assertMariaDbConcurrentReadRestart(failure);
    BookingCalendarManager.Status current =
        calendarManager.status(setup.configurationId(), owner, owner);
    assertNotEquals(original.status().etag(), rotated[0].status().etag());
    assertEquals(rotated[0].status().etag(), current.etag());
    assertEquals(rotated[0].subscriptionUrl(), current.subscriptionUrl());
    assertEquals(
        ApiInventorySharingMode.OWNER_GROUPS,
        instrumentApiMgr.getApiInstrumentById(setup.instrumentId(), owner).getSharingMode());
  }

  @Test
  public void sourceWriteAndCombinedCreatePersistTheReturnedCredential() {
    User owner = createInitAndLoginAnyUser();
    ItemSetup setup = itemConfiguration(owner, "Mixed source create");

    BookingCalendarManager.Created created =
        new TransactionTemplate(getTxMger())
            .execute(
                ignored -> {
                  ApiInstrument permissionSource =
                      instrumentApiMgr.getApiInstrumentById(setup.instrumentId(), owner);
                  permissionSource.setSharingMode(ApiInventorySharingMode.OWNER_ONLY);
                  instrumentApiMgr.updateApiInstrument(permissionSource, owner);
                  return calendarManager.createOrRotate(
                      setup.configurationId(), owner, owner, "\"inactive\"");
                });

    assertItemWinner(setup.configurationId(), created);
  }

  @Test
  public void sourceWriteAndCombinedRotatePersistTheReturnedCredential() {
    User owner = createInitAndLoginAnyUser();
    ItemSetup setup = itemConfiguration(owner, "Mixed source rotate");
    BookingCalendarManager.Created original =
        calendarManager.createOrRotate(setup.configurationId(), owner, owner, "\"inactive\"");

    BookingCalendarManager.Created rotated =
        new TransactionTemplate(getTxMger())
            .execute(
                ignored -> {
                  ApiInstrument permissionSource =
                      instrumentApiMgr.getApiInstrumentById(setup.instrumentId(), owner);
                  permissionSource.setSharingMode(ApiInventorySharingMode.OWNER_ONLY);
                  instrumentApiMgr.updateApiInstrument(permissionSource, owner);
                  return calendarManager.createOrRotate(
                      setup.configurationId(), owner, owner, original.status().etag());
                });

    assertNotEquals(original.status().etag(), rotated.status().etag());
    assertNotEquals(original.subscriptionUrl(), rotated.subscriptionUrl());
    assertItemWinner(setup.configurationId(), rotated);
  }

  @Test
  public void readCommittedMixedRotationRefreshesAPreinitializedEntity() {
    User owner = createInitAndLoginAnyUser();
    ItemSetup setup = itemConfiguration(owner, "Mixed current refresh");
    BookingCalendarManager.Created original =
        calendarManager.createOrRotate(setup.configurationId(), owner, owner, "\"inactive\"");
    TransactionTemplate competing = requiresNew();
    BookingCalendarManager.Created[] competingWinner = new BookingCalendarManager.Created[1];
    TransactionTemplate outer = new TransactionTemplate(getTxMger());
    outer.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

    BookingCalendarManager.Created finalWinner =
        outer.execute(
            ignored -> {
              ApiInstrument permissionSource =
                  instrumentApiMgr.getApiInstrumentById(setup.instrumentId(), owner);
              assertEquals(
                  original.status().etag(),
                  calendarManager.status(setup.configurationId(), owner, owner).etag());
              competingWinner[0] =
                  competing.execute(
                      other ->
                          calendarManager.createOrRotate(
                              setup.configurationId(), owner, owner, original.status().etag()));
              permissionSource.setSharingMode(ApiInventorySharingMode.OWNER_ONLY);
              instrumentApiMgr.updateApiInstrument(permissionSource, owner);
              BookingCalendarManager.Created current =
                  calendarManager.createOrRotate(
                      setup.configurationId(), owner, owner, competingWinner[0].status().etag());
              assertEquals(
                  current.status().etag(),
                  calendarManager.status(setup.configurationId(), owner, owner).etag());
              return current;
            });

    assertNotEquals(original.status().etag(), competingWinner[0].status().etag());
    assertNotEquals(competingWinner[0].status().etag(), finalWinner.status().etag());
    assertItemWinner(setup.configurationId(), finalWinner);
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

  private ItemSetup itemConfiguration(User owner, String name) {
    ApiInstrument created = createBasicInstrumentForUser(owner, name);
    openTransaction();
    Instrument instrument = instrumentDao.get(created.getId());
    Hibernate.initialize(instrument.getOwner());
    BookableTargetReference target =
        new BookableTargetReference(BookableTargetType.INSTRUMENT, instrument.getId());
    commitTransaction();
    BookingConfiguration configuration =
        configurationManager.createConfiguration(
            new BookingConfigurationManager.Create(
                true, "UTC", new ResolvedBookableTarget(target, instrument)),
            owner,
            owner);
    return new ItemSetup(configuration.getId(), instrument.getId());
  }

  private record ItemSetup(Long configurationId, Long instrumentId) {}

  private TransactionTemplate requiresNew() {
    TransactionTemplate transaction = new TransactionTemplate(getTxMger());
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return transaction;
  }

  private static void assertMariaDbConcurrentReadRestart(RuntimeException failure) {
    Throwable cause = failure;
    boolean currentLockFrame = false;
    StringBuilder messages = new StringBuilder();
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current.getMessage() != null) {
        messages.append(current.getMessage()).append('\n');
      }
      currentLockFrame |=
          Arrays.stream(current.getStackTrace())
              .anyMatch(
                  frame ->
                      frame
                              .getClassName()
                              .equals(
                                  "com.researchspace.booking.dao."
                                      + "BookingCalendarSubscriptionDaoHibernate")
                          && frame.getMethodName().equals("lockCurrent"));
    }
    while (cause != null && !(cause instanceof SQLException)) {
      cause = cause.getCause();
    }
    assertTrue(currentLockFrame);
    assertTrue(messages.toString().contains("BookableItemCalendarSubscription"));
    assertTrue(messages.toString().toLowerCase().contains("for update"));
    assertTrue(cause instanceof SQLException);
    assertEquals(1020, ((SQLException) cause).getErrorCode());
  }

  private void assertItemWinner(Long configurationId, BookingCalendarManager.Created expected) {
    String rawToken = rawToken(expected);
    String tokenHash = CryptoUtils.hashToken(rawToken);
    assertEquals("\"" + tokenHash + "\"", expected.status().etag());
    assertEquals(
        tokenHash,
        jdbcTemplate.queryForObject(
            "SELECT tokenHash FROM BookableItemCalendarSubscription"
                + " WHERE bookingConfiguration_id = ?",
            String.class,
            configurationId));
    assertEquals(
        rawToken,
        jdbcTemplate.queryForObject(
            "SELECT rawToken FROM BookableItemCalendarSubscription"
                + " WHERE bookingConfiguration_id = ?",
            String.class,
            configurationId));
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
