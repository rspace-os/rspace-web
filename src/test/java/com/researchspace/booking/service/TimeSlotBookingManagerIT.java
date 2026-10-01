package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static com.researchspace.testutils.BookingTestSchedules.hours;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventorySharingMode;
import com.researchspace.booking.config.BookingTimeConfig;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingOpeningHoursEntry;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ProtectedResourceAccess;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public class TimeSlotBookingManagerIT extends RealTransactionSpringTestBase {

  private final TimeSlotBookingManager bookingManager;
  private final BookingConfigurationManager configurationManager;
  private final BookingCalendarManager calendarManager;
  private final BookingConfigurationDao configurationDao;
  private final InstrumentDao instrumentDao;
  private final JdbcTemplate jdbcTemplate;
  private final ResourceAccessManager resourceAccessManager;
  private final ProtectedResourceAccess<BookingConfiguration, Long> protectedAccess;
  private final FeatureFlagManager featureFlags;

  private final Clock institutionClock;

  @Autowired
  TimeSlotBookingManagerIT(
      TimeSlotBookingManager bookingManager,
      BookingConfigurationManager configurationManager,
      BookingCalendarManager calendarManager,
      BookingConfigurationDao configurationDao,
      InstrumentDao instrumentDao,
      JdbcTemplate jdbcTemplate,
      ResourceAccessManager resourceAccessManager,
      ProtectedResourceAccess<BookingConfiguration, Long> protectedAccess,
      FeatureFlagManager featureFlags,
      @Qualifier(BookingTimeConfig.INSTITUTION_CLOCK) Clock institutionClock) {
    this.bookingManager = bookingManager;
    this.configurationManager = configurationManager;
    this.calendarManager = calendarManager;
    this.configurationDao = configurationDao;
    this.instrumentDao = instrumentDao;
    this.jdbcTemplate = jdbcTemplate;
    this.resourceAccessManager = resourceAccessManager;
    this.protectedAccess = protectedAccess;
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

  @ParameterizedTest(name = "{0}")
  @EnumSource(SnapshotMutation.class)
  public void editRejectsAccessOrConfigurationChangesCommittedAfterItsInitialReadSnapshot(
      SnapshotMutation mutation) {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument instrument = createBasicInstrumentForUser(owner, "Access snapshot scope");
    Setup setup = persistConfiguration(owner, instrument.getId(), false, 0, 0);
    User requester = owner;
    User newOwner = createInitAndLoginAnyUser();
    Instant start = sevenDaysFromNow();
    User bookingRequester = requester;
    TimeSlotBooking existing =
        bookingManager.createBooking(
            new TimeSlotBookingManager.Create(
                new ResolvedBookableTarget(setup.target(), setup.instrument()),
                Date.from(start),
                Date.from(start.plus(1, ChronoUnit.HOURS)),
                null),
            bookingRequester,
            bookingRequester);
    var competing = new TransactionTemplate(getTxMger());
    competing.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    User subject = bookingRequester;

    Runnable updateFromStaleSnapshot =
        () ->
            new TransactionTemplate(getTxMger())
                .executeWithoutResult(
                    ignored -> {
                      bookingManager.getBooking(existing.getId(), subject).orElseThrow();
                      competing.executeWithoutResult(
                          other -> {
                            if (mutation == SnapshotMutation.CHANGE_ITEM_OWNER) {
                              instrumentDao.get(instrument.getId()).setOwner(newOwner);
                            } else {
                              configurationManager.updateConfiguration(
                                  setup.configurationId(),
                                  new BookingConfigurationManager.Patch(false, null),
                                  owner,
                                  owner);
                            }
                          });
                      if (mutation == SnapshotMutation.CHANGE_ITEM_OWNER) {
                        assertTrue(
                            bookingManager
                                .updateBooking(
                                    existing.getId(),
                                    new TimeSlotBookingManager.Patch(
                                        null, null, true, "Revoked edit", null),
                                    subject,
                                    subject)
                                .isEmpty());
                      } else {
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
                      }
                    });
    assertThrows(BookingConcurrentModificationException.class, updateFromStaleSnapshot::run);

    if (mutation == SnapshotMutation.CHANGE_ITEM_OWNER) {
      assertTrue(
          bookingManager
              .updateBooking(
                  existing.getId(),
                  new TimeSlotBookingManager.Patch(null, null, true, "Revoked edit", null),
                  subject,
                  subject)
              .isEmpty());
    }
  }

  @Test
  public void membershipRevocationRejectsWritesWithAnAlreadyInitializedSubject() throws Exception {
    super.setUp();
    User owner = createInitAndLoginAnyUser();
    User booker = createInitAndLoginAnyUser();
    Group group =
        new TransactionTemplate(getTxMger())
            .execute(
                ignored -> {
                  Group created =
                      new Group("booking" + UUID.randomUUID().toString().substring(0, 8), piUser);
                  created.setDisplayName(created.getUniqueName());
                  perFactory
                      .createDefaultGlobalGroupPermissions(created)
                      .forEach(created::addPermission);
                  created = grpMgr.saveGroup(created, piUser);
                  grpMgr.addMembersToGroup(
                      created.getId(),
                      List.of(piUser, owner, booker),
                      piUser.getUsername(),
                      null,
                      piUser);
                  permissionUtils.refreshCache();
                  return created;
                });
    ApiInstrument instrument = createBasicInstrumentForUser(owner, "Membership snapshot scope");
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored ->
                instrumentDao
                    .get(instrument.getId())
                    .setSharingMode(
                        com.researchspace.model.inventory.InventoryRecord.InventorySharingMode
                            .OWNER_GROUPS));
    Setup setup = persistConfiguration(owner, instrument.getId(), false, 0, 0);
    Instant start = sevenDaysFromNow();
    var create =
        new TimeSlotBookingManager.Create(
            new ResolvedBookableTarget(setup.target(), setup.instrument()),
            Date.from(start),
            Date.from(start.plus(1, ChronoUnit.HOURS)),
            null);
    var booking = bookingManager.createBooking(create, booker, booker);
    var link = calendarManager.create(setup.configurationId(), booker, booker);
    String token =
        java.net.URI.create(link.subscriptionUrl()).getRawQuery().substring("token=".length());
    var competing = new TransactionTemplate(getTxMger());
    competing.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    logoutAndLoginAs(piUser);
    User staleBooker =
        new TransactionTemplate(getTxMger())
            .execute(
                ignored -> {
                  User stale = userDao.get(booker.getId());
                  stale.getGroups().forEach(g -> g.getMembers().size());
                  assertTrue(
                      stale.getGroups().stream().anyMatch(g -> g.getId().equals(group.getId())));
                  return stale;
                });
    competing.executeWithoutResult(
        other -> grpMgr.removeUserFromGroup(booker.getUsername(), group.getId(), piUser));
    assertTrue(
        bookingManager
            .updateBooking(
                booking.getId(),
                new TimeSlotBookingManager.Patch(null, null, true, "Must not be saved", null),
                staleBooker,
                staleBooker)
            .isEmpty());
    assertThrows(
        org.apache.shiro.authz.AuthorizationException.class,
        () -> bookingManager.createBooking(create, staleBooker, staleBooker));
    grpMgr.addUserToGroup(
        booker.getUsername(), group.getId(), com.researchspace.model.RoleInGroup.DEFAULT);
    org.junit.jupiter.api.Assertions.assertInstanceOf(
        BookingCalendarManager.NotFound.class,
        calendarManager.feed(token, java.util.Locale.UK, new Date()));
  }

  @Test
  public void lossAndRestoreInOneSourceTransactionRetainsTheExactCalendarToken() throws Exception {
    super.setUp();
    User owner = createInitAndLoginAnyUser();
    User booker = createInitAndLoginAnyUser();
    Group group = accessGroup(owner, booker, null);
    ApiInstrument instrument = createBasicInstrumentForUser(owner, "Loss restore token scope");
    setInstrumentSharing(instrument.getId(), ApiInventorySharingMode.OWNER_GROUPS, owner);
    Setup setup = persistConfiguration(owner, instrument.getId(), false, 0, 0);
    BookingCalendarManager.Created original =
        calendarManager.create(setup.configurationId(), booker, booker);

    logoutAndLoginAs(piUser);
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored -> {
              grpMgr.removeUserFromGroup(booker.getUsername(), group.getId(), piUser);
              grpMgr.addUserToGroup(
                  booker.getUsername(), group.getId(), com.researchspace.model.RoleInGroup.DEFAULT);
            });

    BookingCalendarManager.Status retained =
        calendarManager.status(setup.configurationId(), booker, booker);
    assertEquals(original.status().etag(), retained.etag());
    assertEquals(original.subscriptionUrl(), retained.subscriptionUrl());
  }

  @Test
  public void bookerToViewerAndRedundantPermissionWritesRetainTheExactCalendarToken()
      throws Exception {
    super.setUp();
    User owner = createInitAndLoginAnyUser();
    User labAdmin = createInitAndLoginAnyUser();
    accessGroup(owner, labAdmin, labAdmin.getUsername());
    ApiInstrument instrument = createBasicInstrumentForUser(owner, "Booker viewer token scope");
    setInstrumentSharing(instrument.getId(), ApiInventorySharingMode.OWNER_GROUPS, owner);
    Setup setup = persistConfiguration(owner, instrument.getId(), false, 0, 0);
    BookingCalendarManager.Created original =
        calendarManager.create(setup.configurationId(), labAdmin, labAdmin);

    setInstrumentSharing(instrument.getId(), ApiInventorySharingMode.OWNER_ONLY, owner);
    BookingCalendarManager.Status viewer =
        calendarManager.status(setup.configurationId(), labAdmin, labAdmin);
    assertEquals(original.status().etag(), viewer.etag());
    assertEquals(original.subscriptionUrl(), viewer.subscriptionUrl());

    setInstrumentSharing(instrument.getId(), ApiInventorySharingMode.OWNER_ONLY, owner);
    BookingCalendarManager.Status redundant =
        calendarManager.status(setup.configurationId(), labAdmin, labAdmin);
    assertEquals(original.status().etag(), redundant.etag());
    assertEquals(original.subscriptionUrl(), redundant.subscriptionUrl());
  }

  @Test
  public void blockedRevocationCommitsBeforeAConcurrentPermissionRestore() throws Exception {
    super.setUp();
    User owner = createInitAndLoginAnyUser();
    User booker = createInitAndLoginAnyUser();
    accessGroup(owner, booker, null);
    ApiInstrument instrument = createBasicInstrumentForUser(owner, "Atomic token revocation scope");
    setInstrumentSharing(instrument.getId(), ApiInventorySharingMode.OWNER_GROUPS, owner);
    Setup setup = persistConfiguration(owner, instrument.getId(), false, 0, 0);
    BookingCalendarManager.Created original =
        calendarManager.create(setup.configurationId(), booker, booker);
    String rawToken =
        java.net.URI.create(original.subscriptionUrl()).getRawQuery().substring("token=".length());
    ApiInstrument revoke = instrumentApiMgr.getApiInstrumentById(instrument.getId(), owner);
    revoke.setSharingMode(ApiInventorySharingMode.OWNER_ONLY);
    ApiInstrument restore = instrumentApiMgr.getApiInstrumentById(instrument.getId(), owner);
    restore.setSharingMode(ApiInventorySharingMode.OWNER_GROUPS);

    CountDownLatch legacyFenceLocked = new CountDownLatch(1);
    CountDownLatch releaseLegacyFence = new CountDownLatch(1);
    CountDownLatch sourceWritten = new CountDownLatch(1);
    CountDownLatch restoreStarted = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(3);
    try {
      Future<?> holder =
          pool.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          ignored -> {
                            jdbcTemplate.queryForObject(
                                "SELECT id FROM BookingConfigurationDefaults WHERE id = 1 FOR"
                                    + " UPDATE",
                                Long.class);
                            legacyFenceLocked.countDown();
                            await(releaseLegacyFence);
                          }));
      assertTrue(legacyFenceLocked.await(10, TimeUnit.SECONDS));

      Future<?> revocation =
          pool.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          ignored -> {
                            instrumentApiMgr.updateApiInstrument(revoke, owner);
                            sourceWritten.countDown();
                          }));
      assertTrue(sourceWritten.await(10, TimeUnit.SECONDS));

      Future<?> restoration =
          pool.submit(
              () -> {
                restoreStarted.countDown();
                new TransactionTemplate(getTxMger())
                    .executeWithoutResult(
                        ignored -> instrumentApiMgr.updateApiInstrument(restore, owner));
              });
      assertTrue(restoreStarted.await(10, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> restoration.get(250, TimeUnit.MILLISECONDS));

      releaseLegacyFence.countDown();
      holder.get(20, TimeUnit.SECONDS);
      revocation.get(20, TimeUnit.SECONDS);
      restoration.get(20, TimeUnit.SECONDS);

      assertEquals(
          ApiInventorySharingMode.OWNER_GROUPS,
          instrumentApiMgr.getApiInstrumentById(instrument.getId(), owner).getSharingMode());
      assertEquals(
          Integer.valueOf(0),
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM BookableItemCalendarSubscription"
                  + " WHERE user_id = ? AND bookingConfiguration_id = ?",
              Integer.class,
              booker.getId(),
              setup.configurationId()));
      org.junit.jupiter.api.Assertions.assertInstanceOf(
          BookingCalendarManager.NotFound.class,
          calendarManager.feed(rawToken, java.util.Locale.UK, new Date()));
    } finally {
      releaseLegacyFence.countDown();
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  private Group accessGroup(User owner, User member, String labAdminUsername) {
    return new TransactionTemplate(getTxMger())
        .execute(
            ignored -> {
              Group created =
                  new Group("booking" + UUID.randomUUID().toString().substring(0, 8), piUser);
              created.setDisplayName(created.getUniqueName());
              perFactory
                  .createDefaultGlobalGroupPermissions(created)
                  .forEach(created::addPermission);
              created = grpMgr.saveGroup(created, piUser);
              grpMgr.addMembersToGroup(
                  created.getId(),
                  List.of(piUser, owner, member),
                  piUser.getUsername(),
                  labAdminUsername,
                  piUser);
              if (labAdminUsername != null) {
                grpMgr.authorizeLabAdminToViewAll(member.getId(), piUser, created.getId(), true);
              }
              permissionUtils.refreshCache();
              return created;
            });
  }

  private void setInstrumentSharing(
      Long instrumentId, ApiInventorySharingMode sharingMode, User owner) {
    ApiInstrument update = instrumentApiMgr.getApiInstrumentById(instrumentId, owner);
    update.setSharingMode(sharingMode);
    instrumentApiMgr.updateApiInstrument(update, owner);
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

  @Test
  public void sharedBufferGapIsSymmetricAcrossInsertionOrdersAndAllowsItsExactBoundary() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Buffer boundary scope");
    Setup setup = persistConfiguration(owner, created.getId(), false, 1L, 10, 20);
    ResolvedBookableTarget target = new ResolvedBookableTarget(setup.target(), setup.instrument());
    Instant start =
        LocalDate.ofInstant(institutionClock.instant(), ZoneOffset.UTC)
            .plusDays(7)
            .atTime(10, 0)
            .toInstant(ZoneOffset.UTC);

    create(target, owner, start, start.plus(1, ChronoUnit.HOURS));

    Instant afterStart15 = start.plus(1, ChronoUnit.HOURS).plus(15, ChronoUnit.MINUTES);
    assertThrows(
        BookingBufferConflictException.class,
        () -> create(target, owner, afterStart15, afterStart15.plus(1, ChronoUnit.HOURS)));
    Instant afterStart19 = start.plus(1, ChronoUnit.HOURS).plus(19, ChronoUnit.MINUTES);
    assertThrows(
        BookingBufferConflictException.class,
        () -> create(target, owner, afterStart19, afterStart19.plus(1, ChronoUnit.HOURS)));
    Instant afterStart20 = start.plus(1, ChronoUnit.HOURS).plus(20, ChronoUnit.MINUTES);
    create(target, owner, afterStart20, afterStart20.plus(1, ChronoUnit.HOURS));

    Instant laterStart = start.plus(1, ChronoUnit.DAYS);
    create(target, owner, laterStart, laterStart.plus(1, ChronoUnit.HOURS));
    Instant beforeStart15 = laterStart.minus(1, ChronoUnit.HOURS).minus(15, ChronoUnit.MINUTES);
    assertThrows(
        BookingBufferConflictException.class,
        () -> create(target, owner, beforeStart15, beforeStart15.plus(1, ChronoUnit.HOURS)));
    Instant beforeStart19 = laterStart.minus(1, ChronoUnit.HOURS).minus(19, ChronoUnit.MINUTES);
    assertThrows(
        BookingBufferConflictException.class,
        () -> create(target, owner, beforeStart19, beforeStart19.plus(1, ChronoUnit.HOURS)));
    Instant beforeStart20 = laterStart.minus(1, ChronoUnit.HOURS).minus(20, ChronoUnit.MINUTES);
    create(target, owner, beforeStart20, beforeStart20.plus(1, ChronoUnit.HOURS));
  }

  @Test
  public void zeroBuffersAllowTouchingRawBookingIntervals() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Zero buffer boundary scope");
    Setup setup = persistConfiguration(owner, created.getId(), false, 0, 0);
    ResolvedBookableTarget target = new ResolvedBookableTarget(setup.target(), setup.instrument());
    Instant start =
        LocalDate.ofInstant(institutionClock.instant(), ZoneOffset.UTC)
            .plusDays(7)
            .atTime(10, 0)
            .toInstant(ZoneOffset.UTC);

    create(target, owner, start, start.plus(1, ChronoUnit.HOURS));
    create(target, owner, start.plus(1, ChronoUnit.HOURS), start.plus(2, ChronoUnit.HOURS));
  }

  @Test
  public void oversizedCalendarSourceDoesNotMarkTheCallerTransactionForRollback() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Oversized calendar scope");
    Setup setup = persistConfiguration(owner, created.getId(), false, 0, 0);
    ResolvedBookableTarget target = new ResolvedBookableTarget(setup.target(), setup.instrument());
    Instant start = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    bookingManager.createBooking(
        new TimeSlotBookingManager.Create(
            target, Date.from(start), Date.from(start.plus(1, ChronoUnit.HOURS)), null),
        owner,
        owner);
    bookingManager.createBooking(
        new TimeSlotBookingManager.Create(
            target,
            Date.from(start.plus(2, ChronoUnit.HOURS)),
            Date.from(start.plus(3, ChronoUnit.HOURS)),
            null),
        owner,
        owner);

    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored ->
                assertThrows(
                    TimeSlotBookingManager.CalendarSourceTooLargeException.class,
                    () ->
                        bookingManager.getCalendarSource(
                            setup.configurationId(), owner, new Date(), 1)));
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored ->
                assertThrows(
                    TimeSlotBookingManager.CalendarSourceTooLargeException.class,
                    () -> bookingManager.getUserCalendarSource(owner, new Date(), 1)));
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

  private static Instant sevenDaysFromNow() {
    return Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
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

  private TimeSlotBooking create(
      ResolvedBookableTarget target, User owner, Instant start, Instant end) {
    return bookingManager.createBooking(
        new TimeSlotBookingManager.Create(target, Date.from(start), Date.from(end), null),
        owner,
        owner);
  }

  private Setup persistConfiguration(
      User owner,
      Long instrumentId,
      boolean allowDoubleBooking,
      long bufferBeforeMinutes,
      long bufferAfterMinutes) {
    return persistConfiguration(
        owner, instrumentId, allowDoubleBooking, null, bufferBeforeMinutes, bufferAfterMinutes);
  }

  private Setup persistConfiguration(
      User owner,
      Long instrumentId,
      boolean allowDoubleBooking,
      Long slotGranularityMinutes,
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
                    slotGranularityMinutes,
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

  private enum SnapshotMutation {
    CHANGE_ITEM_OWNER,
    DISABLE_CONFIGURATION
  }

  private record Setup(
      Long configurationId, BookableTargetReference target, Instrument instrument) {}
}
