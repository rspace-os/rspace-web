package com.researchspace.booking.service;

import static com.researchspace.booking.service.TimeSlotBookingManagerIT.sevenDaysFromNow;
import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventorySharingMode;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.service.TimeSlotBookingManagerIT.Setup;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ProtectedResourceAccess;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public class TimeSlotBookingManagerAccessSnapshotIT extends RealTransactionSpringTestBase {

  private final TimeSlotBookingManager bookingManager;
  private final BookingConfigurationManager configurationManager;
  private final BookingCalendarManager calendarManager;
  private final BookingConfigurationDao configurationDao;
  private final InstrumentDao instrumentDao;
  private final JdbcTemplate jdbcTemplate;
  private final ResourceAccessManager resourceAccessManager;
  private final ProtectedResourceAccess<BookingConfiguration, Long> protectedAccess;
  private final FeatureFlagManager featureFlags;

  @Autowired
  TimeSlotBookingManagerAccessSnapshotIT(
      TimeSlotBookingManager bookingManager,
      BookingConfigurationManager configurationManager,
      BookingCalendarManager calendarManager,
      BookingConfigurationDao configurationDao,
      InstrumentDao instrumentDao,
      JdbcTemplate jdbcTemplate,
      ResourceAccessManager resourceAccessManager,
      ProtectedResourceAccess<BookingConfiguration, Long> protectedAccess,
      FeatureFlagManager featureFlags) {
    this.bookingManager = bookingManager;
    this.configurationManager = configurationManager;
    this.calendarManager = calendarManager;
    this.configurationDao = configurationDao;
    this.instrumentDao = instrumentDao;
    this.jdbcTemplate = jdbcTemplate;
    this.resourceAccessManager = resourceAccessManager;
    this.protectedAccess = protectedAccess;
    this.featureFlags = featureFlags;
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
  public void cancellationReasonPersistsInBookingAndEnversHistory() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument instrument = createBasicInstrumentForUser(owner, "Cancellation reason history");
    Setup setup = persistConfiguration(owner, instrument.getId(), false, 0, 0);
    Instant start = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    TimeSlotBookingManager.Create create =
        new TimeSlotBookingManager.Create(
            new ResolvedBookableTarget(setup.target(), setup.instrument()),
            Date.from(start),
            Date.from(start.plus(1, ChronoUnit.HOURS)),
            null);
    TimeSlotBooking booking = bookingManager.createBooking(create, owner, owner);

    var cancelled =
        bookingManager
            .updateBooking(
                booking.getId(),
                new TimeSlotBookingManager.Patch(
                    null,
                    null,
                    false,
                    null,
                    BookingState.CANCELLED,
                    "  Instrument needs recalibration  "),
                booking.getVersion(),
                owner,
                owner)
            .orElseThrow();

    assertEquals("Instrument needs recalibration", cancelled.getCancellationReason());
    assertEquals(
        "Instrument needs recalibration",
        jdbcTemplate.queryForObject(
            "SELECT cancellationReason FROM TimeSlotBooking WHERE id = ?",
            String.class,
            booking.getId()));
    assertEquals(
        "Instrument needs recalibration",
        jdbcTemplate.queryForObject(
            "SELECT cancellationReason FROM TimeSlotBooking_AUD"
                + " WHERE id = ? AND cancellationReason IS NOT NULL ORDER BY REV DESC LIMIT 1",
            String.class,
            booking.getId()));
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

  private enum SnapshotMutation {
    CHANGE_ITEM_OWNER,
    DISABLE_CONFIGURATION
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

  private static void await(CountDownLatch latch) {
    TimeSlotBookingManagerConcurrencyIT.await(latch);
  }
}
