package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.dao.CommunicationDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

public class BookingOwnerNotificationIT extends RealTransactionSpringTestBase {

  private final TimeSlotBookingManager bookingManager;
  private final BookingConfigurationManager configurationManager;
  private final InstrumentDao instrumentDao;
  private final CommunicationDao communicationDao;
  private final FeatureFlagManager featureFlags;

  @Autowired
  BookingOwnerNotificationIT(
      TimeSlotBookingManager bookingManager,
      BookingConfigurationManager configurationManager,
      InstrumentDao instrumentDao,
      CommunicationDao communicationDao,
      FeatureFlagManager featureFlags) {
    this.bookingManager = bookingManager;
    this.configurationManager = configurationManager;
    this.instrumentDao = instrumentDao;
    this.communicationDao = communicationDao;
    this.featureFlags = featureFlags;
  }

  private boolean originalBookingBaseline;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    User sysadmin = getSysAdminUser();
    originalBookingBaseline =
        featureFlags.getFeatureFlag(BOOKING_ENABLED, sysadmin).orElseThrow().isBaselineValue();
    featureFlags.updateFeatureFlag(
        BOOKING_ENABLED, new FeatureFlagManager.Patch(true, false, null), sysadmin, sysadmin);
  }

  @AfterEach
  public void restoreBookingBaseline() throws Exception {
    User sysadmin = getSysAdminUser();
    featureFlags.updateFeatureFlag(
        BOOKING_ENABLED,
        new FeatureFlagManager.Patch(originalBookingBaseline, false, null),
        sysadmin,
        sysadmin);
    super.tearDown();
  }

  @Test
  public void anotherUserBookingPersistsOneCreatedNotificationForCurrentOwner() throws Exception {
    User owner = createInitAndLoginAnyUser();
    BookingSetup setup = createBookingSetup(owner, "Owner notification create");
    User booker = createInitAndLoginAnyUser();
    shareInstrumentWithBooker(setup, booker);

    createBooking(setup, booker);

    List<Notification> notifications =
        BookingNotificationTestFixtures.notificationsFor(
            communicationMgr, owner, NotificationType.NOTIFICATION_BOOKING_CREATED);
    assertEquals(1, notifications.size());
    assertEquals(booker.getId(), notifications.get(0).getOriginator().getId());
    assertPersistedForRecipient(notifications.get(0), owner);
  }

  @Test
  public void ownBookingPersistsNotificationForOwner() throws Exception {
    User owner = createInitAndLoginAnyUser();
    BookingSetup setup = createBookingSetup(owner, "Owner notification create");
    User booker = owner;
    shareInstrumentWithBooker(setup, booker);

    createBooking(setup, booker);

    List<Notification> notifications =
        BookingNotificationTestFixtures.notificationsFor(
            communicationMgr, owner, NotificationType.NOTIFICATION_BOOKING_CREATED);
    assertEquals(1, notifications.size());
    assertEquals(booker.getId(), notifications.get(0).getOriginator().getId());
    assertPersistedForRecipient(notifications.get(0), owner);
  }

  @Test
  public void directCancellationPersistsCancellationNotificationForOwner() throws Exception {
    User owner = createInitAndLoginAnyUser();
    BookingSetup setup = createBookingSetup(owner, "Owner notification cancel");
    User booker = createInitAndLoginAnyUser();
    shareInstrumentWithBooker(setup, booker);
    TimeSlotBooking booking = createBooking(setup, booker);

    bookingManager
        .updateBooking(
            booking.getId(),
            new TimeSlotBookingManager.Patch(null, null, false, null, BookingState.CANCELLED),
            booker,
            booker)
        .orElseThrow();

    List<Notification> notifications =
        BookingNotificationTestFixtures.notificationsFor(
            communicationMgr, owner, NotificationType.NOTIFICATION_BOOKING_CANCELLED);
    assertEquals(1, notifications.size());
    assertEquals(booker.getId(), notifications.get(0).getOriginator().getId());
    assertPersistedForRecipient(notifications.get(0), owner);
    assertEquals(
        0,
        BookingNotificationTestFixtures.notificationsFor(
                communicationMgr, booker, NotificationType.NOTIFICATION_BOOKING_CANCELLED)
            .size());
  }

  @Test
  public void archiveCancellationPersistsOneNotificationPerCancelledBooking() throws Exception {
    User owner = createInitAndLoginAnyUser();
    BookingSetup setup = createBookingSetup(owner, "Owner notification archive");
    User booker = createInitAndLoginAnyUser();
    shareInstrumentWithBooker(setup, booker);
    createBooking(setup, booker);

    User sysadmin = owner;
    createBookingAtNextHour(setup, booker);
    BookingConfiguration archived =
        configurationManager
            .archiveConfiguration(
                setup.configuration().getId(),
                setup.configuration().getConfigurationVersion(),
                sysadmin,
                sysadmin)
            .orElseThrow();

    assertNotNull(archived);
    List<Notification> notifications =
        BookingNotificationTestFixtures.notificationsFor(
            communicationMgr, owner, NotificationType.NOTIFICATION_BOOKING_CANCELLED);
    assertEquals(2, notifications.size());
    assertEquals(sysadmin.getId(), notifications.get(0).getOriginator().getId());
    assertPersistedForRecipient(notifications.get(0), owner);
    List<Notification> requesterNotifications =
        BookingNotificationTestFixtures.notificationsFor(
            communicationMgr, booker, NotificationType.NOTIFICATION_BOOKING_CANCELLED);
    assertEquals(2, requesterNotifications.size());
    assertEquals(sysadmin.getId(), requesterNotifications.get(0).getOriginator().getId());
    assertPersistedForRecipient(requesterNotifications.get(0), booker);
    assertEquals(BookingConfigurationState.ARCHIVED, archived.getState());
  }

  @Test
  public void rolledBackBookingLeavesNoOwnerNotification() {
    User owner = createInitAndLoginAnyUser();
    BookingSetup setup = createBookingSetup(owner, "Owner notification rollback");
    User booker = createInitAndLoginAnyUser();
    shareInstrumentWithBooker(setup, booker);
    TimeSlotBookingManager.Create create = createCommand(setup);

    TransactionTemplate transaction = new TransactionTemplate(getTxMger());
    assertThrows(
        BookingNotificationTestFixtures.ForcedRollback.class,
        () ->
            transaction.executeWithoutResult(
                ignored -> {
                  bookingManager.createBooking(create, booker, booker);
                  throw new BookingNotificationTestFixtures.ForcedRollback();
                }));

    assertEquals(
        0,
        BookingNotificationTestFixtures.notificationsFor(
                communicationMgr, owner, NotificationType.NOTIFICATION_BOOKING_CREATED)
            .size());
  }

  private TimeSlotBooking createBookingAtNextHour(BookingSetup setup, User booker) {
    var command = createCommand(setup);
    return bookingManager.createBooking(
        new TimeSlotBookingManager.Create(
            setup.target(),
            Date.from(command.start().toInstant().plus(2, ChronoUnit.HOURS)),
            Date.from(command.end().toInstant().plus(2, ChronoUnit.HOURS)),
            null),
        booker,
        booker);
  }

  private TimeSlotBooking createBooking(BookingSetup setup, User booker) {
    return bookingManager.createBooking(createCommand(setup), booker, booker);
  }

  private TimeSlotBookingManager.Create createCommand(BookingSetup setup) {
    Instant start = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    return new TimeSlotBookingManager.Create(
        setup.target(), Date.from(start), Date.from(start.plus(1, ChronoUnit.HOURS)), null);
  }

  private BookingSetup createBookingSetup(User owner, String name) {
    ApiInstrument created = createBasicInstrumentForUser(owner, name);
    openTransaction();
    Instrument instrument = instrumentDao.get(created.getId());
    Hibernate.initialize(instrument.getOwner());
    BookableTargetReference target =
        new BookableTargetReference(BookableTargetType.INSTRUMENT, created.getId());
    commitTransaction();
    BookingConfiguration configuration =
        configurationManager.createConfiguration(
            new BookingConfigurationManager.Create(
                true, "UTC", new ResolvedBookableTarget(target, instrument)),
            owner,
            owner);
    return new BookingSetup(owner, configuration, new ResolvedBookableTarget(target, instrument));
  }

  private void shareInstrumentWithBooker(BookingSetup setup, User booker) {
    if (setup.owner().getId().equals(booker.getId())) {
      return;
    }
    try {
      createGroupForUsersWithDefaultPi(setup.owner(), booker);
    } catch (com.researchspace.model.record.IllegalAddChildOperation exception) {
      throw new IllegalStateException("Could not grant booking access to test booker", exception);
    }
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored ->
                instrumentDao
                    .get(setup.configuration().getTarget().id())
                    .setSharingMode(InventoryRecord.InventorySharingMode.OWNER_GROUPS));
    permissionUtils.refreshCache();
  }

  private void assertPersistedForRecipient(Notification notification, User recipient)
      throws Exception {
    doInTransaction(
        () -> {
          Notification persisted =
              (Notification) communicationDao.getWithTargets(notification.getId()).orElseThrow();
          assertEquals(1, persisted.getRecipients().size());
          assertEquals(
              recipient.getId(),
              persisted.getRecipients().iterator().next().getRecipient().getId());
        });
  }

  private record BookingSetup(
      User owner, BookingConfiguration configuration, ResolvedBookableTarget target) {}
}
