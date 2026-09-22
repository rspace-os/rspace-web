package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.CommunicationNotifyPolicy;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.NotificationConfig;
import com.researchspace.testutils.TestFactory;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class BookingNotificationServiceTest {

  private static final long BOOKING_ID = 42L;
  private static final long INSTRUMENT_ID = 12L;

  private final InstrumentDao instrumentDao = mock(InstrumentDao.class);
  private final CommunicationManager communicationManager = mock(CommunicationManager.class);
  private final BookingNotificationService service =
      new BookingNotificationService(instrumentDao, communicationManager, new JsonMessageSource());

  @ParameterizedTest
  @EnumSource(
      value = NotificationType.class,
      names = {"NOTIFICATION_BOOKING_CREATED", "NOTIFICATION_BOOKING_CANCELLED"})
  void notifiesCurrentOwnerWithRenderedMessageAndConfig(NotificationType notificationType) {
    User actor = user("actor");
    User owner = user("owner");
    Instrument instrument = instrument(owner, "<Mic & scope>");
    TimeSlotBooking booking = booking(BOOKING_ID, BookingEventKind.BOOKING, actor);
    when(instrumentDao.getSafeNull(INSTRUMENT_ID)).thenReturn(Optional.of(instrument));

    service.notify(booking, actor, notificationType);

    ArgumentCaptor<NotificationConfig> config = ArgumentCaptor.forClass(NotificationConfig.class);
    ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
    verify(communicationManager).notify(eq(actor), isNull(), config.capture(), message.capture());

    assertEquals(notificationType, config.getValue().getNotificationType());
    assertEquals(true, config.getValue().isBroadcast());
    assertEquals(CommunicationNotifyPolicy.ALWAYS_NOTIFY, config.getValue().getPolicyOverride());
    assertEquals(false, config.getValue().isRecordAuthorisationRequired());
    assertEquals(java.util.Set.of(owner), config.getValue().getNotificationTargetsOverride());
    String expectedMessage =
        notificationType == NotificationType.NOTIFICATION_BOOKING_CREATED
            ? "Booking 42 was created for instrument &lt;Mic &amp; scope&gt; (IN12) from "
                + "2026-01-02T03:04:05Z to 2026-01-02T04:04:05Z."
            : "Booking 42 for instrument &lt;Mic &amp; scope&gt; (IN12) was cancelled. It was "
                + "scheduled from 2026-01-02T03:04:05Z to 2026-01-02T04:04:05Z.";
    assertEquals(expectedMessage, message.getValue());
  }

  @Test
  void resolvesTheOwnerAtNotificationTimeAfterOwnershipTransfer() {
    User previousOwner = user("previous-owner");
    User currentOwner = user("current-owner");
    Instrument instrument = instrument(currentOwner, "Microscope");
    TimeSlotBooking booking = booking(BOOKING_ID, BookingEventKind.BOOKING, previousOwner);
    when(instrumentDao.getSafeNull(INSTRUMENT_ID)).thenReturn(Optional.of(instrument));

    service.notify(booking, previousOwner, NotificationType.NOTIFICATION_BOOKING_CREATED);

    ArgumentCaptor<NotificationConfig> config = ArgumentCaptor.forClass(NotificationConfig.class);
    verify(communicationManager)
        .notify(eq(previousOwner), isNull(), config.capture(), any(String.class));
    assertEquals(
        java.util.Set.of(currentOwner), config.getValue().getNotificationTargetsOverride());
  }

  @Test
  void suppressesSelfNotificationsAndDisabledOwnerPreferences() {
    User actor = user("actor");
    Instrument instrument = instrument(actor, "Microscope");
    when(instrumentDao.getSafeNull(INSTRUMENT_ID)).thenReturn(Optional.of(instrument));

    service.notify(
        booking(BOOKING_ID, BookingEventKind.BOOKING, actor),
        actor,
        NotificationType.NOTIFICATION_BOOKING_CREATED);

    User owner = user("owner");
    owner.setPreference(
        new UserPreference(
            Preference.NOTIFICATION_BOOKING_CANCELLED_PREF, owner, Boolean.FALSE.toString()));
    instrument.setOwner(owner);
    service.notify(
        booking(BOOKING_ID, BookingEventKind.BOOKING, actor),
        actor,
        NotificationType.NOTIFICATION_BOOKING_CANCELLED);

    verify(communicationManager, never()).notify(any(), any(), any(), any());
  }

  @Test
  void skipsMaintenanceBookingsAndMissingOrDeletedInstruments() {
    User actor = user("actor");
    User owner = user("owner");
    Instrument instrument = instrument(owner, "Microscope");
    when(instrumentDao.getSafeNull(INSTRUMENT_ID)).thenReturn(Optional.of(instrument));

    service.notify(
        booking(BOOKING_ID, BookingEventKind.MAINTENANCE, actor),
        actor,
        NotificationType.NOTIFICATION_BOOKING_CREATED);
    verify(instrumentDao, never()).getSafeNull(INSTRUMENT_ID);

    when(instrumentDao.getSafeNull(INSTRUMENT_ID)).thenReturn(Optional.empty());
    service.notify(
        booking(BOOKING_ID, BookingEventKind.BOOKING, actor),
        actor,
        NotificationType.NOTIFICATION_BOOKING_CREATED);

    instrument.setRecordDeleted(true);
    when(instrumentDao.getSafeNull(INSTRUMENT_ID)).thenReturn(Optional.of(instrument));
    service.notify(
        booking(BOOKING_ID, BookingEventKind.BOOKING, actor),
        actor,
        NotificationType.NOTIFICATION_BOOKING_CREATED);

    verify(communicationManager, never()).notify(any(), any(), any(), any());
  }

  private static User user(String username) {
    return TestFactory.createAnyUser(username);
  }

  private static Instrument instrument(User owner, String name) {
    Instrument instrument = new Instrument();
    instrument.setId(INSTRUMENT_ID);
    instrument.setName(name);
    instrument.setOwner(owner);
    return instrument;
  }

  private static TimeSlotBooking booking(long id, BookingEventKind kind, User requester) {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.replaceTarget(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, INSTRUMENT_ID));
    TimeSlotBooking booking = new TimeSlotBooking();
    booking.setId(id);
    booking.setBookingConfiguration(configuration);
    booking.setRequester(requester);
    booking.setKind(kind);
    booking.setStartTime(Date.from(Instant.parse("2026-01-02T03:04:05Z")));
    booking.setEndTime(Date.from(Instant.parse("2026-01-02T04:04:05Z")));
    return booking;
  }
}
