package com.researchspace.booking.service;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.BookingNotificationSubscriptionDao;
import com.researchspace.dao.CommunicationDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableItemNotificationSubscription;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingDisplaySettings;
import com.researchspace.model.booking.BookingTimezoneMode;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.CommunicationTarget;
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
import com.researchspace.service.impl.CommunicationEmailBroadcaster;
import com.researchspace.service.impl.CommunicationManagerImpl;
import com.researchspace.service.impl.EmailContentGenerator;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Real-transaction coverage for booking-notification recipient selection and delivery. */
class BookingNotificationRecipientsIT extends RealTransactionSpringTestBase {

  private static final NotificationType CREATED = NotificationType.NOTIFICATION_BOOKING_CREATED;

  @Autowired private TimeSlotBookingManager bookingManager;
  @Autowired private BookingConfigurationManager configurationManager;
  @Autowired private BookingConfigurationDao configurationDao;
  @Autowired private BookingNotificationSubscriptionDao subscriptions;
  @Autowired private BookingNotificationRecipientReader recipientReader;
  @Autowired private InstrumentDao instrumentDao;
  @Autowired private CommunicationDao communicationDao;
  @Autowired private FeatureFlagManager featureFlags;
  @Autowired private BookingDisplayPreferencesManager displayPreferences;
  @Autowired private EmailContentGenerator emailContentGenerator;

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
    assertNoNotification(actor);

    assertEquals(
        Set.of(owner.getEmail(), booker.getEmail()),
        emailCapture.deliveries().stream()
            .flatMap(delivery -> delivery.addresses().stream())
            .collect(Collectors.toSet()));
    assertEquals(2, emailCapture.deliveries().size());
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

  @Test
  void usesFreshDetachedPreferencesWhenOuterBookingSessionHasOldRecipientValues() throws Exception {
    User owner = createInitAndLoginAnyUser();
    User subscriber = createInitAndLoginAnyUser();
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

    assertSingleNotification(subscriber, "America/Los_Angeles");
    assertEquals(1, emailCapture.deliveries().size());
    assertEquals(List.of(subscriber.getEmail()), emailCapture.deliveries().get(0).addresses());
    assertTrue(
        emailCapture.deliveries().get(0).notificationMessage().contains("America/Los_Angeles"));
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
    org.junit.jupiter.api.Assertions.assertThrows(
        ForcedRollback.class,
        () ->
            transaction.executeWithoutResult(
                ignored -> {
                  createBooking(setup, owner);
                  throw new ForcedRollback();
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
              org.junit.jupiter.api.Assertions.assertEquals(
                  java.util.Optional.of(expectedRole),
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
    List<Notification> notifications = notificationsFor(recipient);
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
    assertTrue(notificationsFor(recipient).isEmpty(), recipient.getUsername());
  }

  private List<Notification> notificationsFor(User recipient) {
    return communicationMgr
        .getNewNotificationsForUser(
            recipient.getUsername(),
            PaginationCriteria.createDefaultForClass(CommunicationTarget.class))
        .getResults()
        .stream()
        .filter(notification -> notification.getNotificationType() == CREATED)
        .toList();
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
        .findEnabledByInstrument(instrumentId);
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
  }

  private static final class ForcedRollback extends RuntimeException {}
}
