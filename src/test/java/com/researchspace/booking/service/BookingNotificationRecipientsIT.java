package com.researchspace.booking.service;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.researchspace.Constants;
import com.researchspace.api.ConcurrentChangeClassifier;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.booking.dao.BookingCalendarPermissionGateDao;
import com.researchspace.booking.dao.BookingCalendarSubscriptionDao;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.BookingNotificationSubscriptionDao;
import com.researchspace.dao.CommunicationDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableItemNotificationSubscription;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingDisplaySettings;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.BookingTimezoneMode;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.permissions.RecordSharingACL;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.Broadcaster;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.EmailContent;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.NotificationPostCommitExecutor;
import com.researchspace.service.impl.CommunicationEmailBroadcaster;
import com.researchspace.service.impl.CommunicationManagerImpl;
import com.researchspace.service.impl.EmailContentGenerator;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import jakarta.ws.rs.NotFoundException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.hibernate.Hibernate;
import org.hibernate.engine.jdbc.connections.internal.DatasourceConnectionProviderImpl;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class BookingNotificationRecipientsIT extends RealTransactionSpringTestBase {

  private static final NotificationType CREATED = NotificationType.NOTIFICATION_BOOKING_CREATED;
  private static final NotificationType CANCELLED = NotificationType.NOTIFICATION_BOOKING_CANCELLED;

  private final TimeSlotBookingManager bookingManager;
  private final BookingConfigurationManager configurationManager;
  private final BookingConfigurationDao configurationDao;
  private final BookingCalendarPermissionGateDao permissionGate;
  private final BookingNotificationSubscriptionDao subscriptions;
  private final BookingNotificationRecipientReader recipientReader;
  private final InstrumentDao instrumentDao;
  private final CommunicationDao communicationDao;
  private final FeatureFlagManager featureFlags;
  private final BookingDisplayPreferencesManager displayPreferences;
  private final EmailContentGenerator emailContentGenerator;
  private final BasicDataSource pool;
  private final BookingNotificationSubscriptionManager subscriptionManager;

  @Autowired
  BookingNotificationRecipientsIT(
      TimeSlotBookingManager bookingManager,
      BookingConfigurationManager configurationManager,
      BookingConfigurationDao configurationDao,
      BookingCalendarPermissionGateDao permissionGate,
      BookingNotificationSubscriptionDao subscriptions,
      BookingNotificationRecipientReader recipientReader,
      InstrumentDao instrumentDao,
      CommunicationDao communicationDao,
      FeatureFlagManager featureFlags,
      BookingDisplayPreferencesManager displayPreferences,
      EmailContentGenerator emailContentGenerator,
      BasicDataSource pool,
      BookingNotificationSubscriptionManager subscriptionManager) {
    this.bookingManager = bookingManager;
    this.configurationManager = configurationManager;
    this.configurationDao = configurationDao;
    this.permissionGate = permissionGate;
    this.subscriptions = subscriptions;
    this.recipientReader = recipientReader;
    this.instrumentDao = instrumentDao;
    this.communicationDao = communicationDao;
    this.featureFlags = featureFlags;
    this.displayPreferences = displayPreferences;
    this.emailContentGenerator = emailContentGenerator;
    this.pool = pool;
    this.subscriptionManager = subscriptionManager;
  }

  private boolean originalBookingBaseline;
  private CommunicationManagerImpl communicationManager;
  private List<Broadcaster> originalBroadcasters;
  private RecordingEmailBroadcast emailCapture;
  private BookingNotificationRecipientReader recipientReaderTarget;
  private BookingNotificationSubscriptionDao originalReaderSubscriptions;

  @BeforeEach
  void setUpNotifications() throws Exception {
    super.setUp();
    User sysadmin = getSysAdminUser();
    originalBookingBaseline =
        featureFlags.getFeatureFlag(BOOKING_ENABLED, sysadmin).orElseThrow().isBaselineValue();
    setBookingBaseline(true, sysadmin);

    communicationManager = AopTestUtils.getUltimateTargetObject(communicationMgr);
    @SuppressWarnings("unchecked")
    List<Broadcaster> configured =
        (List<Broadcaster>) ReflectionTestUtils.getField(communicationManager, "broadcasters");
    originalBroadcasters = new ArrayList<>(configured);
    emailCapture = new RecordingEmailBroadcast();
    List<Broadcaster> broadcasters = new ArrayList<>(configured);
    broadcasters.add(
        new CommunicationEmailBroadcaster(
            emailCapture, emailContentGenerator, "http://localhost:8080/"));
    communicationManager.setBroadcasters(broadcasters);
  }

  @AfterEach
  void restoreNotifications() throws Exception {
    restoreRecipientDao();
    if (communicationManager != null && originalBroadcasters != null) {
      communicationManager.setBroadcasters(originalBroadcasters);
    }
    if (featureFlags != null) {
      setBookingBaseline(originalBookingBaseline, getSysAdminUser());
    }
    super.tearDown();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void ownBookingEmailFollowsOwnerPreference(boolean emailEnabled) throws Exception {
    User owner = createInitAndLoginAnyUser();
    BookingSetup setup = createBookingSetup(owner, "Own booking email " + getRandomName(6));
    setRecipientPreferences(owner, true, emailEnabled, "UTC");
    createBooking(setup, owner);
    assertSingleNotification(owner, "UTC");
    assertEquals(emailEnabled ? 1 : 0, emailCapture.deliveries().size());
  }

  @Test
  void subscriptionPutDeniesRevocationCommittedAfterOuterSnapshot() throws Exception {
    User owner = createInitAndLoginAnyUser();
    var setup = createBookingSetup(owner, "Current subscription access " + getRandomName(6));
    String version =
        BookingNotificationSubscriptionManager.etag(
            subscriptionManager.get(setup.configurationId(), owner, owner));
    CountDownLatch snapshot = new CountDownLatch(1);
    CountDownLatch revoked = new CountDownLatch(1);
    ExecutorService worker = Executors.newSingleThreadExecutor();
    try {
      Future<?> put =
          worker.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          status -> {
                            User cached =
                                sessionFactory.getCurrentSession().get(User.class, owner.getId());
                            assertTrue(cached.isEnabled());
                            if (Boolean.getBoolean("booking.snapshotIsolation")) {
                              assertSnapshotIsolation();
                            }
                            snapshot.countDown();
                            await(revoked);
                            assertTrue(
                                cached.isEnabled(),
                                "the outer session retains its old account state");
                            subscriptionManager.replace(
                                setup.configurationId(), true, version, owner, owner);
                          }));
      assertTrue(snapshot.await(15, TimeUnit.SECONDS));
      User fresh = userMgr.get(owner.getId());
      fresh.setEnabled(false);
      userMgr.save(fresh);
      revoked.countDown();
      var failure = assertThrows(ExecutionException.class, () -> put.get(15, TimeUnit.SECONDS));
      if (Boolean.getBoolean("booking.snapshotIsolation")) {
        assertInstanceOf(
            BookingNotificationSubscriptionConflictException.class, failure.getCause());
      } else {
        assertInstanceOf(NotFoundException.class, failure.getCause());
      }
    } finally {
      revoked.countDown();
      worker.shutdownNow();
    }
  }

  @Test
  void saturatedPoolSubscriptionPutJoinsTheRequestTransaction() throws Exception {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Subscription pool regression");
    Instrument instrument =
        new TransactionTemplate(getTxMger())
            .execute(
                status -> {
                  Instrument loaded = instrumentDao.get(created.getId());
                  Hibernate.initialize(loaded.getOwner());
                  return loaded;
                });
    var target =
        new ResolvedBookableTarget(
            new BookableTargetReference(BookableTargetType.INSTRUMENT, created.getId()),
            instrument);
    var configuration =
        configurationManager.createConfiguration(
            new BookingConfigurationManager.Create(true, "UTC", target), owner, owner);
    var manager = applicationContext.getBean(BookingNotificationSubscriptionManager.class);
    String version =
        BookingNotificationSubscriptionManager.etag(
            manager.get(configuration.getId(), owner, owner));
    int previousTotal = pool.getMaxTotal();
    long previousWait = pool.getMaxWaitMillis();
    try {
      pool.setMaxTotal(Math.max(1, pool.getNumActive() + 1));
      pool.setMaxWaitMillis(500);
      ((GenericObjectPool<?>) ReflectionTestUtils.invokeMethod(pool, "getConnectionPool")).clear();
      new TransactionTemplate(getTxMger())
          .executeWithoutResult(
              status -> {
                sessionFactory.getCurrentSession().doWork(connection -> {});
                assertTrue(
                    manager.replace(configuration.getId(), true, version, owner, owner).enabled());
              });
    } finally {
      pool.setMaxTotal(previousTotal);
      pool.setMaxWaitMillis(previousWait);
    }
  }

  @Test
  void deliversToOwnerBookerAndViewerWithEachUsersEventEmailAndTimezonePreferences()
      throws Exception {
    User owner = createInitAndLoginAnyUser();
    User booker = createInitAndLoginAnyUser();
    User viewer = createAndSaveUser(getRandomName(10), Constants.PI_ROLE);
    setUpUserWithoutCustomContent(viewer);
    User eventDisabled = createInitAndLoginAnyUser();
    User actor = createInitAndLoginAnyUser();
    BookingSetup setup = createBookingSetup(owner, "Subscriber roles " + getRandomName(6));
    Group bookerGroup = createGroupForUsersWithDefaultPi(owner, booker, eventDisabled, actor);
    createGroupForPiAndUsers(viewer, new User[] {viewer, owner});
    shareWithBookersOnly(setup.instrumentId(), bookerGroup);

    assertBookingRole(setup.configurationId(), owner, BookingResourceRoleScheme.OWNER);
    assertBookingRole(setup.configurationId(), booker, BookingResourceRoleScheme.BOOKER);
    assertBookingRole(setup.configurationId(), viewer, BookingResourceRoleScheme.VIEWER);

    ensureSubscribed(setup.instrumentId(), owner, booker, viewer, eventDisabled, actor);
    setRecipientPreferences(owner, true, true, "Europe/Berlin");
    setRecipientPreferences(booker, true, true, "America/Los_Angeles");
    setRecipientPreferences(viewer, true, false, "Asia/Tokyo");
    setRecipientPreferences(eventDisabled, false, true, "Europe/Paris");
    setRecipientPreferences(actor, true, true, "UTC");

    logoutAndLoginAs(actor);
    createBooking(setup, actor);

    assertSingleNotification(owner, "Europe/Berlin");
    assertSingleNotification(booker, "America/Los_Angeles");
    assertSingleNotification(viewer, "Asia/Tokyo");
    assertNoNotification(eventDisabled);
    assertSingleNotification(actor, "UTC");

    assertEquals(
        Set.of(owner.getEmail(), booker.getEmail(), actor.getEmail()),
        emailCapture.deliveries().stream()
            .flatMap(delivery -> delivery.addresses().stream())
            .collect(Collectors.toSet()));
    assertEquals(3, emailCapture.deliveries().size());
    assertTrue(
        emailCapture.deliveries().stream()
            .anyMatch(delivery -> delivery.notificationMessage().contains("Europe/Berlin")));
    assertTrue(
        emailCapture.deliveries().stream()
            .anyMatch(delivery -> delivery.notificationMessage().contains("America/Los_Angeles")));
    assertFalse(
        emailCapture.deliveries().stream()
            .flatMap(delivery -> delivery.addresses().stream())
            .anyMatch(viewer.getEmail()::equals));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void requesterCancellationOverrideDeliversInAppAndHonorsEmailPreference(boolean emailEnabled)
      throws Exception {
    User owner = createInitAndLoginAnyUser();
    User requester = createInitAndLoginAnyUser();
    userMgr.setPreference(Preference.BOOKING_NOTIFY_OWN_ACTIONS, "false", owner.getUsername());
    BookingSetup setup = createBookingSetup(owner, "Cancellation requester " + getRandomName(6));
    Group bookerGroup = createGroupForUsersWithDefaultPi(owner, requester);
    shareWithBookersOnly(setup.instrumentId(), bookerGroup);
    setRecipientPreferences(requester, false, emailEnabled, "UTC");
    userMgr.setPreference(
        Preference.NOTIFICATION_BOOKING_CANCELLED_PREF,
        Boolean.FALSE.toString(),
        requester.getUsername());

    logoutAndLoginAs(requester);
    TimeSlotBooking booking = createBooking(setup, requester);
    assertNoNotification(requester);
    emailCapture.clear();

    logoutAndLoginAs(owner);
    bookingManager
        .updateBooking(
            booking.getId(),
            new TimeSlotBookingManager.Patch(null, null, false, null, BookingState.CANCELLED),
            owner,
            owner)
        .orElseThrow();

    assertSingleNotification(requester, "UTC", CANCELLED);
    if (emailEnabled) {
      assertEquals(1, emailCapture.deliveries().size());
      assertEquals(List.of(requester.getEmail()), emailCapture.deliveries().get(0).addresses());
    } else {
      assertTrue(emailCapture.deliveries().isEmpty());
    }
  }

  @ParameterizedTest
  @EnumSource(RequesterIneligibility.class)
  void excludesInactiveOrBookingDisabledRequesterFromCancellationNotification(
      RequesterIneligibility ineligibility) throws Exception {
    User owner = createInitAndLoginAnyUser();
    User requester = createInitAndLoginAnyUser();
    userMgr.setPreference(Preference.BOOKING_NOTIFY_OWN_ACTIONS, "false", owner.getUsername());
    BookingSetup setup = createBookingSetup(owner, "Ineligible requester " + getRandomName(6));
    Group bookerGroup = createGroupForUsersWithDefaultPi(owner, requester);
    shareWithBookersOnly(setup.instrumentId(), bookerGroup);
    setRecipientPreferences(requester, false, false, "UTC");

    logoutAndLoginAs(requester);
    TimeSlotBooking booking = createBooking(setup, requester);
    assertNoNotification(requester);
    emailCapture.clear();
    applyRequesterIneligibility(requester, ineligibility);

    logoutAndLoginAs(owner);
    bookingManager
        .updateBooking(
            booking.getId(),
            new TimeSlotBookingManager.Patch(null, null, false, null, BookingState.CANCELLED),
            owner,
            owner)
        .orElseThrow();

    assertNoNotification(requester, CANCELLED);
    assertTrue(emailCapture.deliveries().isEmpty(), ineligibility.name());
  }

  @Test
  void usesTheBookingSnapshotWhenOuterSessionAlreadyLoadedPreferences() throws Exception {
    User owner = createInitAndLoginAnyUser();
    User subscriber = createInitAndLoginAnyUser();
    userMgr.setPreference(Preference.BOOKING_NOTIFY_OWN_ACTIONS, "false", owner.getUsername());
    BookingSetup setup = createBookingSetup(owner, "Detached notification " + getRandomName(6));
    Group bookerGroup = createGroupForUsersWithDefaultPi(owner, subscriber);
    shareWithBookersOnly(setup.instrumentId(), bookerGroup);
    ensureSubscribed(setup.instrumentId(), owner, subscriber);
    setRecipientPreferences(subscriber, false, false, "Asia/Tokyo");
    logoutAndLoginAs(owner);

    TransactionTemplate outerBooking = new TransactionTemplate(getTxMger());
    TransactionTemplate competingWrite = new TransactionTemplate(getTxMger());
    competingWrite.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    outerBooking.executeWithoutResult(
        ignored -> {
          User staleSubscriber = userDao.get(subscriber.getId());
          Hibernate.initialize(staleSubscriber.getUserPreferences());
          assertFalse(staleSubscriber.wantsNotificationFor(CREATED));
          assertFalse(
              staleSubscriber
                  .getValueForPreference(Preference.BROADCAST_NOTIFICATIONS_BY_EMAIL)
                  .getValueAsBoolean());

          competingWrite.executeWithoutResult(
              suspended -> setRecipientPreferences(subscriber, true, true, "America/Los_Angeles"));

          createBooking(setup, owner);
        });

    assertNoNotification(subscriber);
    assertTrue(emailCapture.deliveries().isEmpty());
  }

  @Test
  @EnabledIfSystemProperty(named = "booking.snapshotIsolation", matches = "true")
  void snapshotRejectionOfNotificationUpdateIsADomainConflict() throws Exception {
    User owner = createInitAndLoginAnyUser();
    var setup = createBookingSetup(owner, "Snapshot subscription " + getRandomName(6));
    long version =
        new TransactionTemplate(getTxMger())
            .execute(
                ignored ->
                    subscriptions
                        .findByUserAndInstrument(owner.getId(), setup.instrumentId())
                        .orElseThrow()
                        .getVersion());
    var target = AopTestUtils.getUltimateTargetObject(subscriptionManager);
    var original =
        (BookingNotificationSubscriptionDao) ReflectionTestUtils.getField(target, "subscriptions");
    var paused =
        spy((BookingNotificationSubscriptionDao) AopTestUtils.getUltimateTargetObject(original));
    CountDownLatch lockingRead = new CountDownLatch(1);
    CountDownLatch updated = new CountDownLatch(1);
    doAnswer(
            call -> {
              assertSnapshotIsolation();
              lockingRead.countDown();
              await(updated);
              return call.callRealMethod();
            })
        .when(paused)
        .findByUserAndInstrumentForUpdate(owner.getId(), setup.instrumentId());
    ReflectionTestUtils.setField(target, "subscriptions", paused);
    ExecutorService workers = Executors.newSingleThreadExecutor();
    try {
      Future<?> update =
          workers.submit(
              () ->
                  subscriptionManager.replace(
                      setup.configurationId(), false, "\"v" + version + "-000\"", owner, owner));
      assertTrue(lockingRead.await(20, TimeUnit.SECONDS));
      new TransactionTemplate(getTxMger())
          .executeWithoutResult(
              ignored ->
                  sessionFactory
                      .getCurrentSession()
                      .createMutationQuery(
                          "update BookableItemNotificationSubscription set enabled = false, version"
                              + " = version + 1 where instrument.id = :id")
                      .setParameter("id", setup.instrumentId())
                      .executeUpdate());
      updated.countDown();
      var failure = assertThrows(ExecutionException.class, () -> update.get(20, TimeUnit.SECONDS));
      assertInstanceOf(BookingNotificationSubscriptionConflictException.class, failure.getCause());
    } finally {
      updated.countDown();
      workers.shutdownNow();
      workers.awaitTermination(20, TimeUnit.SECONDS);
      ReflectionTestUtils.setField(target, "subscriptions", original);
    }
  }

  @Test
  @EnabledIfSystemProperty(named = "booking.snapshotIsolation", matches = "true")
  void snapshotRejectionAtDefaultsFenceIsABookingConflict() throws Exception {
    User owner = createInitAndLoginAnyUser();
    var setup = createBookingSetup(owner, "Snapshot defaults " + getRandomName(6));
    var bookingTarget = AopTestUtils.getUltimateTargetObject(bookingManager);
    Object access = ReflectionTestUtils.getField(bookingTarget, "accessManager");
    var permissionsTarget = AopTestUtils.getUltimateTargetObject(access);
    var original =
        (BookingCalendarSubscriptionDao)
            ReflectionTestUtils.getField(permissionsTarget, "subscriptions");
    var paused =
        spy((BookingCalendarSubscriptionDao) AopTestUtils.getUltimateTargetObject(original));
    CountDownLatch fenceRead = new CountDownLatch(1);
    CountDownLatch committed = new CountDownLatch(1);
    doAnswer(
            call -> {
              assertSnapshotIsolation();
              fenceRead.countDown();
              await(committed);
              return call.callRealMethod();
            })
        .when(paused)
        .lockPermissionFence();
    ReflectionTestUtils.setField(permissionsTarget, "subscriptions", paused);
    ExecutorService workers = Executors.newSingleThreadExecutor();
    try {
      Future<?> create = workers.submit(() -> createBooking(setup, owner));
      assertTrue(fenceRead.await(20, TimeUnit.SECONDS));
      new TransactionTemplate(getTxMger())
          .executeWithoutResult(
              ignored ->
                  sessionFactory
                      .getCurrentSession()
                      .createNativeMutationQuery(
                          "update BookingConfigurationDefaults set configurationVersion ="
                              + " configurationVersion + 1 where id = 1")
                      .executeUpdate());
      committed.countDown();
      var failure = assertThrows(ExecutionException.class, () -> create.get(20, TimeUnit.SECONDS));
      var conflict =
          assertInstanceOf(BookingConcurrentModificationException.class, failure.getCause());
      assertTrue(ConcurrentChangeClassifier.isConcurrentChange(conflict));
    } finally {
      committed.countDown();
      workers.shutdownNow();
      workers.awaitTermination(20, TimeUnit.SECONDS);
      ReflectionTestUtils.setField(permissionsTarget, "subscriptions", original);
    }
  }

  private void assertSnapshotIsolation() {
    int enabled =
        sessionFactory
            .getCurrentSession()
            .doReturningWork(
                connection -> {
                  try (var statement = connection.createStatement();
                      var result = statement.executeQuery("select @@innodb_snapshot_isolation")) {
                    result.next();
                    return result.getInt(1);
                  }
                });
    assertEquals(1, enabled);
  }

  @Test
  void firstSubscriptionsForDifferentUsersAndInstrumentsDoNotTakeGapLocks() throws Exception {
    User first = createInitAndLoginAnyUser();
    var firstSetup = createBookingSetup(first, "First subscription " + getRandomName(6));
    User second = createInitAndLoginAnyUser();
    var secondSetup = createBookingSetup(second, "Second subscription " + getRandomName(6));
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored ->
                sessionFactory
                    .getCurrentSession()
                    .createMutationQuery(
                        "delete from BookableItemNotificationSubscription where instrument.id in"
                            + " :ids")
                    .setParameterList(
                        "ids", List.of(firstSetup.instrumentId(), secondSetup.instrumentId()))
                    .executeUpdate());
    var managerTarget = AopTestUtils.getUltimateTargetObject(subscriptionManager);
    var original =
        (BookingNotificationSubscriptionDao)
            ReflectionTestUtils.getField(managerTarget, "subscriptions");
    var daoSpy =
        spy((BookingNotificationSubscriptionDao) AopTestUtils.getUltimateTargetObject(original));
    CountDownLatch absentRowsRead = new CountDownLatch(2);
    CountDownLatch insert = new CountDownLatch(1);
    doAnswer(
            call -> {
              Object result = call.callRealMethod();
              absentRowsRead.countDown();
              await(insert);
              return result;
            })
        .when(daoSpy)
        .findByUserAndInstrument(anyLong(), anyLong());
    ReflectionTestUtils.setField(managerTarget, "subscriptions", daoSpy);
    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      Future<?> one =
          workers.submit(
              () ->
                  subscriptionManager.replace(
                      firstSetup.configurationId(), true, "\"vabsent-000\"", first, first));
      Future<?> two =
          workers.submit(
              () ->
                  subscriptionManager.replace(
                      secondSetup.configurationId(), true, "\"vabsent-000\"", second, second));
      if (!absentRowsRead.await(20, TimeUnit.SECONDS)) {
        if (one.isDone()) one.get();
        if (two.isDone()) two.get();
        fail(
            "Only "
                + (2 - absentRowsRead.getCount())
                + " subscription reads reached the insertion barrier");
      }
      insert.countDown();
      one.get(20, TimeUnit.SECONDS);
      two.get(20, TimeUnit.SECONDS);
      new TransactionTemplate(getTxMger())
          .executeWithoutResult(
              ignored -> {
                assertTrue(
                    subscriptions
                        .findByUserAndInstrument(first.getId(), firstSetup.instrumentId())
                        .orElseThrow()
                        .isEnabled());
                assertTrue(
                    subscriptions
                        .findByUserAndInstrument(second.getId(), secondSetup.instrumentId())
                        .orElseThrow()
                        .isEnabled());
              });
    } finally {
      insert.countDown();
      workers.shutdownNow();
      workers.awaitTermination(20, TimeUnit.SECONDS);
      ReflectionTestUtils.setField(managerTarget, "subscriptions", original);
    }
  }

  @Test
  void recipientSelectionQueryCountDoesNotGrowPerFollower() throws Exception {
    User owner = createInitAndLoginAnyUser();
    BookingSetup setup = createBookingSetup(owner, "Batched recipients " + getRandomName(6));
    ensureSubscribed(setup.instrumentId(), owner);
    var statistics = sessionFactory.getStatistics();
    boolean original = statistics.isStatisticsEnabled();
    statistics.setStatisticsEnabled(true);
    try {
      long before = statistics.getPrepareStatementCount();
      new TransactionTemplate(getTxMger())
          .executeWithoutResult(
              ignored ->
                  recipientReader.selectRecipients(setup.instrumentId(), CREATED, owner.getId()));
      long one = statistics.getPrepareStatementCount() - before;
      List<User> followers = new ArrayList<>();
      for (int i = 0; i < 12; i++) followers.add(createInitAndLoginAnyUser());
      Group group =
          createGroupForUsersWithDefaultPi(
              Stream.concat(Stream.of(owner), followers.stream()).toArray(User[]::new));
      shareWithBookersOnly(setup.instrumentId(), group);
      ensureSubscribed(setup.instrumentId(), followers.toArray(User[]::new));
      before = statistics.getPrepareStatementCount();
      new TransactionTemplate(getTxMger())
          .executeWithoutResult(
              ignored ->
                  assertEquals(
                      13,
                      recipientReader
                          .selectRecipients(setup.instrumentId(), CREATED, owner.getId())
                          .size()));
      long many = statistics.getPrepareStatementCount() - before;
      assertTrue(many <= one + 2, "one recipient: " + one + ", many recipients: " + many);
    } finally {
      statistics.setStatisticsEnabled(original);
    }
  }

  @Test
  void initializeForInstrumentRacingAnExplicitOffPreservesOff() throws Exception {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created =
        createBasicInstrumentForUser(owner, "Initialization race " + getRandomName(6));
    CountDownLatch offInserted = new CountDownLatch(1);
    CountDownLatch initializerReady = new CountDownLatch(1);
    CountDownLatch commitOff = new CountDownLatch(1);
    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      Future<?> off =
          workers.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          ignored -> {
                            subscriptions.initialize(
                                owner.getId(), created.getId(), false, new Date());
                            offInserted.countDown();
                            await(commitOff);
                          }));
      assertTrue(offInserted.await(20, TimeUnit.SECONDS));
      Future<?> initialization =
          workers.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          ignored -> {
                            Instrument instrument = instrumentDao.get(created.getId());
                            initializerReady.countDown();
                            subscriptionManager.initializeForInstrument(instrument);
                            instrument.getEditInfo().setName("Transaction remains writable");
                          }));
      assertTrue(initializerReady.await(20, TimeUnit.SECONDS));
      commitOff.countDown();
      off.get(20, TimeUnit.SECONDS);
      initialization.get(20, TimeUnit.SECONDS);
      new TransactionTemplate(getTxMger())
          .executeWithoutResult(
              ignored -> {
                assertFalse(
                    subscriptions
                        .findByUserAndInstrument(owner.getId(), created.getId())
                        .orElseThrow()
                        .isEnabled());
                assertEquals(
                    "Transaction remains writable", instrumentDao.get(created.getId()).getName());
              });
    } finally {
      commitOff.countDown();
      workers.shutdownNow();
      workers.awaitTermination(20, TimeUnit.SECONDS);
    }
  }

  @Test
  void saturatedPoolDoesNotBorrowAnotherStackConnectionDuringMutation() throws Exception {
    int capacity = 3;
    int activeBookings = capacity - 1;
    int requestCount = capacity + 2;
    List<User> owners = new ArrayList<>();
    List<ResolvedBookableTarget> targets = new ArrayList<>();
    for (int i = 0; i < requestCount; i++) {
      User owner = createInitAndLoginAnyUser();
      owners.add(owner);
      ApiInstrument created =
          createBasicInstrumentForUser(owner, "Pool booking " + getRandomName(6));
      Instrument instrument =
          new TransactionTemplate(getTxMger())
              .execute(
                  ignored -> {
                    Instrument loaded = instrumentDao.get(created.getId());
                    Hibernate.initialize(loaded.getOwner());
                    return loaded;
                  });
      targets.add(
          new ResolvedBookableTarget(
              new BookableTargetReference(BookableTargetType.INSTRUMENT, created.getId()),
              instrument));
    }
    var provider =
        sessionFactory
            .unwrap(SessionFactoryImplementor.class)
            .getServiceRegistry()
            .getService(ConnectionProvider.class);
    var configurable = assertInstanceOf(DatasourceConnectionProviderImpl.class, provider);
    var originalSource = configurable.getDataSource();
    Set<Thread> mutating = ConcurrentHashMap.newKeySet();
    var stackBorrows = new AtomicInteger();
    var generatorBorrows = new AtomicInteger();
    var observedSource =
        new DelegatingDataSource(originalSource) {
          private void recordBorrow() {
            if (!mutating.contains(Thread.currentThread())) return;
            boolean generator =
                Arrays.stream(Thread.currentThread().getStackTrace())
                    .anyMatch(
                        frame ->
                            frame
                                    .getClassName()
                                    .startsWith("org.hibernate.id.enhanced.TableGenerator")
                                || frame
                                    .getClassName()
                                    .startsWith("org.hibernate.id.enhanced.TableStructure"));
            if (generator) generatorBorrows.incrementAndGet();
            else stackBorrows.incrementAndGet();
          }

          @Override
          public Connection getConnection() throws SQLException {
            recordBorrow();
            return super.getConnection();
          }

          @Override
          public Connection getConnection(String username, String password) throws SQLException {
            recordBorrow();
            return super.getConnection(username, password);
          }
        };
    int originalTotal = pool.getMaxTotal();
    int originalIdle = pool.getMaxIdle();
    long originalWait = pool.getMaxWaitMillis();
    ExecutorService workers = Executors.newFixedThreadPool(requestCount);
    CountDownLatch connectionsTaken = new CountDownLatch(activeBookings);
    CountDownLatch startBookings = new CountDownLatch(1);
    List<CountDownLatch> admission = new ArrayList<>();
    for (int i = 0; i < (requestCount + activeBookings - 1) / activeBookings; i++)
      admission.add(new CountDownLatch(1));
    try {
      var idle = new ArrayList<Connection>();
      int idleCount = pool.getNumIdle();
      for (int i = 0; i < idleCount; i++) idle.add(pool.getConnection());
      pool.setMaxIdle(capacity);
      for (var connection : idle) connection.close();
      // Communication's pre-existing TABLE ID generator borrows an isolated connection for
      // each notification. Reserve one slot for identifier allocation and count it separately.
      pool.setMaxTotal(capacity);
      pool.setMaxWaitMillis(3000);
      configurable.setDataSource(observedSource);
      List<Future<TimeSlotBooking>> results = new ArrayList<>();
      for (int i = 0; i < requestCount; i++) {
        int index = i;
        results.add(
            workers.submit(
                () -> {
                  if (index >= activeBookings) await(admission.get(index / activeBookings));
                  try {
                    return new TransactionTemplate(getTxMger())
                        .execute(
                            ignored -> {
                              sessionFactory.getCurrentSession().doWork(connection -> {});
                              mutating.add(Thread.currentThread());
                              org.springframework.transaction.support
                                  .TransactionSynchronizationManager.registerSynchronization(
                                  new org.springframework.transaction.support
                                      .TransactionSynchronization() {
                                    @Override
                                    public int getOrder() {
                                      return Ordered.HIGHEST_PRECEDENCE;
                                    }

                                    @Override
                                    public void afterCommit() {
                                      mutating.remove(Thread.currentThread());
                                    }

                                    @Override
                                    public void afterCompletion(int status) {
                                      mutating.remove(Thread.currentThread());
                                    }
                                  });
                              if (index < activeBookings) {
                                permissionGate.lockShared();
                                // Both writers own their independent instrument rows before either
                                // enters configuration creation. Exercise the real target locks;
                                // recipient and generator borrow assertions still cover creation.
                                sessionFactory
                                    .getCurrentSession()
                                    .createNativeQuery(
                                        "select id from InstrumentEntity where id = :id for update",
                                        Long.class)
                                    .setParameter("id", targets.get(index).reference().id())
                                    .getSingleResult();
                                connectionsTaken.countDown();
                                await(startBookings);
                              }
                              User owner = owners.get(index);
                              var configuration =
                                  configurationManager.createConfiguration(
                                      new BookingConfigurationManager.Create(
                                          true, "UTC", targets.get(index)),
                                      owner,
                                      owner);
                              return createBooking(
                                  new BookingSetup(
                                      configuration,
                                      targets.get(index),
                                      targets.get(index).reference().id()),
                                  owner);
                            });
                  } finally {
                    mutating.remove(Thread.currentThread());
                  }
                }));
      }
      assertTrue(connectionsTaken.await(20, TimeUnit.SECONDS));
      startBookings.countDown();
      for (int wave = 0; wave < admission.size(); wave++) {
        if (wave > 0) admission.get(wave).countDown();
        for (int i = wave * activeBookings;
            i < Math.min(requestCount, (wave + 1) * activeBookings);
            i++) assertTrue(results.get(i).get(30, TimeUnit.SECONDS).getId() > 0);
      }
      assertEquals(
          0,
          stackBorrows.get(),
          "recipient selection, subscription reads and default insertion must join the caller");
      assertTrue(
          generatorBorrows.get() >= requestCount,
          "identifier-generator borrows are explicitly accounted for");
    } finally {
      startBookings.countDown();
      admission.forEach(CountDownLatch::countDown);
      workers.shutdownNow();
      workers.awaitTermination(30, TimeUnit.SECONDS);
      configurable.setDataSource(originalSource);
      pool.setMaxTotal(originalTotal);
      pool.setMaxIdle(originalIdle);
      pool.setMaxWaitMillis(originalWait);
    }
    for (User owner : owners)
      assertEquals(
          1,
          BookingNotificationTestFixtures.notificationsFor(communicationMgr, owner, CREATED)
              .size());
  }

  @Test
  void selectionLeavesLaterBookingAndUserUpdatesWritable() throws Exception {
    User owner = createInitAndLoginAnyUser();
    BookingSetup setup = createBookingSetup(owner, "Writable recipients " + getRandomName(6));
    ensureSubscribed(setup.instrumentId(), owner);
    TimeSlotBooking booking = createBooking(setup, owner);
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored -> {
              var session = sessionFactory.getCurrentSession();
              recipientReader.selectRecipients(setup.instrumentId(), CREATED, owner.getId());
              assertFalse(session.isDefaultReadOnly());
              User managedOwner = userDao.get(owner.getId());
              assertFalse(session.isReadOnly(managedOwner));
              managedOwner.setFirstName("After selection");
              session.get(TimeSlotBooking.class, booking.getId()).setPurpose("After selection");
            });
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored -> {
              assertEquals("After selection", userDao.get(owner.getId()).getFirstName());
              assertEquals(
                  "After selection",
                  sessionFactory
                      .getCurrentSession()
                      .get(TimeSlotBooking.class, booking.getId())
                      .getPurpose());
            });
  }

  @Test
  void bulkArchiveCancelsBookingsForEveryConfigurationAfterRecipientSelection() throws Exception {
    User owner = createInitAndLoginAnyUser();
    var setups =
        List.of(
            createBookingSetup(owner, "Bulk first " + getRandomName(6)),
            createBookingSetup(owner, "Bulk second " + getRandomName(6)),
            createBookingSetup(owner, "Bulk third " + getRandomName(6)));
    var bookings = new ArrayList<TimeSlotBooking>();
    for (var setup : setups) {
      ensureSubscribed(setup.instrumentId(), owner);
      bookings.add(createBooking(setup, owner));
    }
    User admin = getSysAdminUser();
    var ids = setups.stream().map(setup -> setup.configuration().getId()).toList();
    var filter =
        new FilterExpression.Comparison("id", Operator.IN, new ArrayList<Object>(ids), false);
    var request =
        new ResourceRequest(
            filter,
            List.of(),
            new ResourceRequest.Page(1, 100),
            FieldSelection.all(),
            IncludeTree.empty());
    configurationManager.archiveConfigurations(request, admin, admin);
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored -> {
              for (var booking : bookings)
                assertEquals(
                    BookingState.CANCELLED,
                    sessionFactory
                        .getCurrentSession()
                        .get(TimeSlotBooking.class, booking.getId())
                        .getState());
            });
  }

  @Test
  void deliveryTransactionStartupFailureLeavesBookingAndNotificationsCommitted() throws Exception {
    User owner = createInitAndLoginAnyUser();
    BookingSetup setup = createBookingSetup(owner, "Failed live delivery " + getRandomName(6));
    setRecipientPreferences(owner, true, false, "UTC");
    Object original =
        ReflectionTestUtils.getField(communicationManager, "notificationPostCommitExecutor");
    var unavailable = mock(NotificationPostCommitExecutor.class);
    doThrow(new CannotCreateTransactionException("simulated exhausted notification pool"))
        .when(unavailable)
        .execute(any(Runnable.class));
    ReflectionTestUtils.setField(
        communicationManager, "notificationPostCommitExecutor", unavailable);
    var laterCallbacks = new AtomicInteger();
    try {
      TimeSlotBooking saved =
          new TransactionTemplate(getTxMger())
              .execute(
                  ignored -> {
                    var booking = createBooking(setup, owner);
                    TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                          @Override
                          public void afterCommit() {
                            laterCallbacks.incrementAndGet();
                          }
                        });
                    return booking;
                  });
      assertTrue(bookingManager.getBooking(saved.getId(), owner).isPresent());
      assertSingleNotification(owner, "UTC");
      assertEquals(1, laterCallbacks.get());
      // Main #1341 runs both the delivery and the committed-count refresh through the executor.
      verify(unavailable, times(2)).execute(any(Runnable.class));
      assertTrue(emailCapture.deliveries().isEmpty());
    } finally {
      ReflectionTestUtils.setField(
          communicationManager, "notificationPostCommitExecutor", original);
    }
  }

  @Test
  void bookingRollbackLeavesNoRecipientNotificationsOrEmail() throws Exception {
    User owner = createInitAndLoginAnyUser();
    User subscriber = createInitAndLoginAnyUser();
    BookingSetup setup = createBookingSetup(owner, "Rollback recipients " + getRandomName(6));
    Group bookerGroup = createGroupForUsersWithDefaultPi(owner, subscriber);
    shareWithBookersOnly(setup.instrumentId(), bookerGroup);
    ensureSubscribed(setup.instrumentId(), owner, subscriber);
    logoutAndLoginAs(owner);

    TransactionTemplate transaction = new TransactionTemplate(getTxMger());
    assertThrows(
        BookingNotificationTestFixtures.ForcedRollback.class,
        () ->
            transaction.executeWithoutResult(
                ignored -> {
                  createBooking(setup, owner);
                  throw new BookingNotificationTestFixtures.ForcedRollback();
                }));

    assertNoNotification(owner);
    assertNoNotification(subscriber);
    assertTrue(emailCapture.deliveries().isEmpty());
  }

  @Test
  void recipientChangesCommittedAfterFirstSelectUseOneOriginalSnapshot() throws Exception {
    User owner = createInitAndLoginAnyUser();
    User unsubscribedAfterSnapshot = createInitAndLoginAnyUser();
    User globalPreferenceAfterSnapshot = createAndSaveUser(getRandomName(10), Constants.PI_ROLE);
    setUpUserWithoutCustomContent(globalPreferenceAfterSnapshot);
    User revokedAfterSnapshot = createInitAndLoginAnyUser();
    BookingSetup setup = createBookingSetup(owner, "Recipient snapshot " + getRandomName(6));
    Group bookerGroup =
        createGroupForUsersWithDefaultPi(owner, unsubscribedAfterSnapshot, revokedAfterSnapshot);
    createGroupForPiAndUsers(
        globalPreferenceAfterSnapshot, new User[] {globalPreferenceAfterSnapshot, owner});
    shareWithBookersOnly(setup.instrumentId(), bookerGroup);
    ensureSubscribed(
        setup.instrumentId(),
        owner,
        unsubscribedAfterSnapshot,
        globalPreferenceAfterSnapshot,
        revokedAfterSnapshot);
    setRecipientPreferences(unsubscribedAfterSnapshot, true, true, "America/Los_Angeles");
    setRecipientPreferences(globalPreferenceAfterSnapshot, true, true, "Asia/Tokyo");
    setRecipientPreferences(revokedAfterSnapshot, true, true, "Europe/Paris");

    setRecipientPreferences(owner, true, true, "UTC");
    CountDownLatch firstSelectReturned = new CountDownLatch(1);
    CountDownLatch allowPermissionResolution = new CountDownLatch(1);
    installRecipientSelectPause(
        setup.instrumentId(), firstSelectReturned, allowPermissionResolution);
    logoutAndLoginAs(owner);
    User sysadmin = getSysAdminUser();
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<?> changes =
          pool.submit(
              () -> {
                try {
                  await(firstSelectReturned);
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          ignored -> {
                            BookableItemNotificationSubscription saved =
                                subscriptions
                                    .findByUserAndInstrument(
                                        unsubscribedAfterSnapshot.getId(), setup.instrumentId())
                                    .orElseThrow();
                            saved.setEnabled(false);
                            saved.setUpdatedAt(new Date());
                            subscriptions.saveAndFlush(saved);
                            userMgr.setPreference(
                                Preference.BROADCAST_NOTIFICATIONS_BY_EMAIL,
                                Boolean.FALSE.toString(),
                                unsubscribedAfterSnapshot.getUsername());
                            userMgr.setPreference(
                                Preference.NOTIFICATION_BOOKING_CREATED_PREF,
                                Boolean.FALSE.toString(),
                                globalPreferenceAfterSnapshot.getUsername());
                            Instrument instrument = instrumentDao.get(setup.instrumentId());
                            instrument.setSharingACL(new RecordSharingACL());
                            setBookingBaseline(false, sysadmin);
                            permissionUtils.refreshCache();
                          });
                } finally {
                  allowPermissionResolution.countDown();
                }
              });

      List<BookingNotificationRecipient> selected =
          recipientReader.selectRecipients(setup.instrumentId(), CREATED, owner.getId());
      changes.get(30, TimeUnit.SECONDS);
      assertEquals(
          Set.of(
              owner.getId(),
              unsubscribedAfterSnapshot.getId(),
              globalPreferenceAfterSnapshot.getId(),
              revokedAfterSnapshot.getId()),
          selected.stream().map(recipient -> recipient.user().getId()).collect(Collectors.toSet()));
      assertTrue(
          selected.stream().allMatch(recipient -> recipient.user().wantsNotificationFor(CREATED)));
      assertTrue(
          selected.stream()
              .allMatch(
                  recipient ->
                      recipient
                          .user()
                          .getValueForPreference(Preference.BROADCAST_NOTIFICATIONS_BY_EMAIL)
                          .getValueAsBoolean()));
    } finally {
      allowPermissionResolution.countDown();
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
      restoreRecipientDao();
    }

    setBookingBaseline(true, getSysAdminUser());
    Set<Long> nextSnapshotRecipientIds =
        recipientReader.selectRecipients(setup.instrumentId(), CREATED, Long.MIN_VALUE).stream()
            .map(recipient -> recipient.user().getId())
            .collect(Collectors.toSet());
    assertEquals(Set.of(owner.getId()), nextSnapshotRecipientIds);
  }

  private void assertBookingRole(long configurationId, User user, String expectedRole) {
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored -> {
              BookingConfiguration configuration = configurationDao.get(configurationId);
              User persistedUser = userDao.get(user.getId());
              assertEquals(
                  Optional.of(expectedRole),
                  getBeanOfClass(BookingItemPermissions.class)
                      .resolve(configuration, persistedUser)
                      .effectiveRole());
            });
  }

  private void shareWithBookersOnly(long instrumentId, Group bookers) {
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored -> {
              Instrument instrument = instrumentDao.get(instrumentId);
              instrument.setSharingMode(InventoryRecord.InventorySharingMode.WHITELIST);
              instrument.setSharingACL(
                  RecordSharingACL.createACLForUserOrGroup(bookers, PermissionType.WRITE));
              permissionUtils.refreshCache();
            });
  }

  private void setRecipientPreferences(
      User user, boolean wantsCreated, boolean emailEnabled, String customTimezone) {
    userMgr.setPreference(
        Preference.NOTIFICATION_BOOKING_CREATED_PREF,
        Boolean.toString(wantsCreated),
        user.getUsername());
    userMgr.setPreference(
        Preference.BROADCAST_NOTIFICATIONS_BY_EMAIL,
        Boolean.toString(emailEnabled),
        user.getUsername());
    displayPreferences.replace(
        new BookingDisplaySettings("08:00", "18:00", BookingTimezoneMode.CUSTOM, customTimezone),
        user,
        user);
  }

  private void ensureSubscribed(long instrumentId, User... users) {
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored -> {
              Instrument instrument = instrumentDao.get(instrumentId);
              for (User user : users) {
                BookableItemNotificationSubscription subscription =
                    subscriptions.findByUserAndInstrument(user.getId(), instrumentId).orElse(null);
                if (subscription == null) {
                  subscription =
                      new BookableItemNotificationSubscription(
                          userDao.get(user.getId()), instrument, true, new Date());
                } else {
                  subscription.setEnabled(true);
                  subscription.setUpdatedAt(new Date());
                }
                subscriptions.saveAndFlush(subscription);
              }
            });
  }

  private BookingSetup createBookingSetup(User owner, String name) {
    ApiInstrument created = createBasicInstrumentForUser(owner, name);
    BookableTargetReference target =
        new BookableTargetReference(BookableTargetType.INSTRUMENT, created.getId());
    Instrument instrument =
        new TransactionTemplate(getTxMger())
            .execute(
                ignored -> {
                  Instrument loaded = instrumentDao.get(created.getId());
                  Hibernate.initialize(loaded.getOwner());
                  return loaded;
                });
    ResolvedBookableTarget resolved = new ResolvedBookableTarget(target, instrument);
    BookingConfiguration configuration =
        configurationManager.createConfiguration(
            new BookingConfigurationManager.Create(true, "UTC", resolved), owner, owner);
    return new BookingSetup(configuration, resolved, created.getId());
  }

  private TimeSlotBooking createBooking(BookingSetup setup, User actor) {
    Instant start = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    return bookingManager.createBooking(
        new TimeSlotBookingManager.Create(
            setup.target(), Date.from(start), Date.from(start.plus(1, ChronoUnit.HOURS)), null),
        actor,
        actor);
  }

  private void assertSingleNotification(User recipient, String expectedTimezone) throws Exception {
    assertSingleNotification(recipient, expectedTimezone, CREATED);
  }

  private void assertSingleNotification(
      User recipient, String expectedTimezone, NotificationType notificationType) throws Exception {
    List<Notification> notifications =
        BookingNotificationTestFixtures.notificationsFor(
            communicationMgr, recipient, notificationType);
    assertEquals(1, notifications.size(), recipient.getUsername());
    assertTrue(
        notifications.get(0).getNotificationMessage().contains(expectedTimezone),
        notifications.get(0).getNotificationMessage());
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored -> {
              Notification persisted =
                  (Notification)
                      communicationDao.getWithTargets(notifications.get(0).getId()).orElseThrow();
              assertEquals(1, persisted.getRecipients().size());
              assertEquals(
                  Set.of(recipient.getId()),
                  persisted.getRecipients().stream()
                      .map(target -> target.getRecipient().getId())
                      .collect(Collectors.toSet()));
            });
  }

  private void assertNoNotification(User recipient) {
    assertNoNotification(recipient, CREATED);
  }

  private void assertNoNotification(User recipient, NotificationType notificationType) {
    assertTrue(
        BookingNotificationTestFixtures.notificationsFor(
                communicationMgr, recipient, notificationType)
            .isEmpty(),
        recipient.getUsername());
  }

  private void installRecipientSelectPause(
      long instrumentId, CountDownLatch selected, CountDownLatch continueSelection)
      throws Exception {
    recipientReaderTarget = AopTestUtils.getUltimateTargetObject(recipientReader);
    originalReaderSubscriptions =
        (BookingNotificationSubscriptionDao)
            ReflectionTestUtils.getField(recipientReaderTarget, "subscriptions");
    BookingNotificationSubscriptionDao daoSpy =
        spy(
            (BookingNotificationSubscriptionDao)
                AopTestUtils.getUltimateTargetObject(originalReaderSubscriptions));
    doAnswer(
            invocation -> {
              Object candidates = invocation.callRealMethod();
              selected.countDown();
              await(continueSelection);
              return candidates;
            })
        .when(daoSpy)
        .findEnabledUserIds(instrumentId);
    ReflectionTestUtils.setField(recipientReaderTarget, "subscriptions", daoSpy);
  }

  private void restoreRecipientDao() {
    if (recipientReaderTarget != null && originalReaderSubscriptions != null) {
      ReflectionTestUtils.setField(
          recipientReaderTarget, "subscriptions", originalReaderSubscriptions);
      recipientReaderTarget = null;
      originalReaderSubscriptions = null;
    }
  }

  private void setBookingBaseline(boolean enabled, User sysadmin) {
    featureFlags
        .updateFeatureFlag(
            BOOKING_ENABLED, new FeatureFlagManager.Patch(enabled, false, null), sysadmin, sysadmin)
        .orElseThrow();
  }

  private void applyRequesterIneligibility(User requester, RequesterIneligibility ineligibility) {
    if (ineligibility == RequesterIneligibility.BOOKING_FEATURE_DISABLED) {
      setBookingBaseline(false, getSysAdminUser());
      return;
    }
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored -> {
              User persisted = userDao.get(requester.getId());
              if (ineligibility == RequesterIneligibility.DISABLED) {
                persisted.setEnabled(false);
              } else {
                persisted.setAccountLocked(true);
              }
              userDao.save(persisted);
            });
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(30, TimeUnit.SECONDS)) {
        throw new AssertionError("Timed out waiting for the notification snapshot test");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }

  private record BookingSetup(
      BookingConfiguration configuration, ResolvedBookableTarget target, Long instrumentId) {
    long configurationId() {
      return configuration.getId();
    }
  }

  private record EmailDelivery(List<String> addresses, String notificationMessage) {}

  private enum RequesterIneligibility {
    DISABLED,
    LOCKED,
    BOOKING_FEATURE_DISABLED
  }

  private static final class RecordingEmailBroadcast implements EmailBroadcast {
    private final List<EmailDelivery> recorded = new CopyOnWriteArrayList<>();

    @Override
    public void sendEmail(EmailContent content, List<String> recipients, Communication comm) {
      Notification notification = (Notification) comm;
      recorded.add(
          new EmailDelivery(List.copyOf(recipients), notification.getNotificationMessage()));
    }

    List<EmailDelivery> deliveries() {
      return List.copyOf(recorded);
    }

    void clear() {
      recorded.clear();
    }
  }
}
