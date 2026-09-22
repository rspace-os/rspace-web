package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.dao.CommunicationDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.preference.Preference;
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

/** Integration coverage for persisted RSpace notifications sent to instrument owners. */
public class BookingOwnerNotificationIT extends RealTransactionSpringTestBase {

  @Autowired private TimeSlotBookingManager bookingManager;
  @Autowired private BookingConfigurationManager configurationManager;
  @Autowired private InstrumentDao instrumentDao;
  @Autowired private CommunicationDao communicationDao;
  @Autowired private FeatureFlagManager featureFlags;

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

    createBooking(setup, booker);

    List<Notification> notifications =
        notificationsFor(owner, NotificationType.NOTIFICATION_BOOKING_CREATED);
    assertEquals(1, notifications.size());
    assertEquals(booker.getId(), notifications.get(0).getOriginator().getId());
    assertPersistedForOwner(notifications.get(0), owner);
  }

  @Test
  public void directCancellationPersistsCancellationNotificationForOwner() throws Exception {
    User owner = createInitAndLoginAnyUser();
    BookingSetup setup = createBookingSetup(owner, "Owner notification cancel");
    User booker = createInitAndLoginAnyUser();
    TimeSlotBooking booking = createBooking(setup, booker);

    bookingManager
        .updateBooking(
            booking.getId(),
            new TimeSlotBookingManager.Patch(null, null, false, null, BookingState.CANCELLED),
            booker,
            booker)
        .orElseThrow();

    List<Notification> notifications =
        notificationsFor(owner, NotificationType.NOTIFICATION_BOOKING_CANCELLED);
    assertEquals(1, notifications.size());
    assertEquals(booker.getId(), notifications.get(0).getOriginator().getId());
    assertPersistedForOwner(notifications.get(0), owner);
  }

  @Test
  public void ownerPreferenceFalseSuppressesCreatedNotification() {
    User owner = createInitAndLoginAnyUser();
    userMgr.setPreference(
        Preference.NOTIFICATION_BOOKING_CREATED_PREF,
        Boolean.FALSE.toString(),
        owner.getUsername());
    BookingSetup setup = createBookingSetup(owner, "Owner notification preference");
    User booker = createInitAndLoginAnyUser();

    createBooking(setup, booker);

    assertEquals(0, notificationsFor(owner, NotificationType.NOTIFICATION_BOOKING_CREATED).size());
  }

  @Test
  public void ownerSelfBookingDoesNotCreateOwnerNotification() {
    User owner = createInitAndLoginAnyUser();
    BookingSetup setup = createBookingSetup(owner, "Owner self notification");

    createBooking(setup, owner);

    assertEquals(0, notificationsFor(owner, NotificationType.NOTIFICATION_BOOKING_CREATED).size());
  }

  @Test
  public void archiveCancellationPersistsOneNotificationPerCancelledBooking() throws Exception {
    User owner = createInitAndLoginAnyUser();
    BookingSetup setup = createBookingSetup(owner, "Owner notification archive");
    User booker = createInitAndLoginAnyUser();
    createBooking(setup, booker);

    User sysadmin = getSysAdminUser();
    logoutAndLoginAs(sysadmin);
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
        notificationsFor(owner, NotificationType.NOTIFICATION_BOOKING_CANCELLED);
    assertEquals(1, notifications.size());
    assertEquals(sysadmin.getId(), notifications.get(0).getOriginator().getId());
    assertPersistedForOwner(notifications.get(0), owner);
    assertEquals(BookingConfigurationState.ARCHIVED, archived.getState());
  }

  @Test
  public void rolledBackBookingLeavesNoOwnerNotification() {
    User owner = createInitAndLoginAnyUser();
    BookingSetup setup = createBookingSetup(owner, "Owner notification rollback");
    User booker = createInitAndLoginAnyUser();
    TimeSlotBookingManager.Create create = createCommand(setup);

    TransactionTemplate transaction = new TransactionTemplate(getTxMger());
    assertThrows(
        ForcedRollback.class,
        () ->
            transaction.executeWithoutResult(
                ignored -> {
                  bookingManager.createBooking(create, booker, booker);
                  throw new ForcedRollback();
                }));

    assertEquals(0, notificationsFor(owner, NotificationType.NOTIFICATION_BOOKING_CREATED).size());
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
    return new BookingSetup(configuration, new ResolvedBookableTarget(target, instrument));
  }

  private List<Notification> notificationsFor(User user, NotificationType type) {
    return communicationMgr
        .getNewNotificationsForUser(
            user.getUsername(), PaginationCriteria.createDefaultForClass(CommunicationTarget.class))
        .getResults()
        .stream()
        .filter(notification -> notification.getNotificationType() == type)
        .toList();
  }

  private void assertPersistedForOwner(Notification notification, User owner) throws Exception {
    doInTransaction(
        () -> {
          Notification persisted =
              (Notification) communicationDao.getWithTargets(notification.getId()).orElseThrow();
          assertEquals(1, persisted.getRecipients().size());
          assertEquals(
              owner.getId(), persisted.getRecipients().iterator().next().getRecipient().getId());
        });
  }

  private record BookingSetup(BookingConfiguration configuration, ResolvedBookableTarget target) {}

  private static final class ForcedRollback extends RuntimeException {}
}
