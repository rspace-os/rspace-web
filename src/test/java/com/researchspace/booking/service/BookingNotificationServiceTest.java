package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.comms.data.BookingNotificationData;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.CommunicationNotifyPolicy;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.NotificationConfig;
import com.researchspace.testutils.TestFactory;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.i18n.LocaleContextHolder;

class BookingNotificationServiceTest {

  private static final long BOOKING_ID = 42L;
  private static final long INSTRUMENT_ID = 12L;

  private final InstrumentDao instrumentDao = mock(InstrumentDao.class);
  private final CommunicationManager communicationManager = mock(CommunicationManager.class);
  private final BookingNotificationRecipientReader recipientReader =
      mock(BookingNotificationRecipientReader.class);
  private final BookingNotificationMessageFormatter messageFormatter =
      new BookingNotificationMessageFormatter(new JsonMessageSource());
  private final BookingNotificationService service =
      new BookingNotificationService(
          instrumentDao, communicationManager, recipientReader, messageFormatter);

  @BeforeEach
  void setUp() {
    LocaleContextHolder.setLocale(Locale.US);
  }

  @AfterEach
  void resetLocale() {
    LocaleContextHolder.resetLocaleContext();
  }

  @ParameterizedTest
  @EnumSource(
      value = NotificationType.class,
      names = {"NOTIFICATION_BOOKING_CREATED", "NOTIFICATION_BOOKING_CANCELLED"})
  void notifiesSelectedSubscriberWithRenderedMessageAndConfig(NotificationType notificationType) {
    User actor = user(1L, "actor");
    User recipient = user(2L, "recipient");
    Instrument instrument = instrument("<Mic & scope>");
    TimeSlotBooking booking = booking(BOOKING_ID, BookingEventKind.BOOKING, actor);
    when(instrumentDao.getSafeNull(INSTRUMENT_ID)).thenReturn(Optional.of(instrument));
    when(recipientReader.selectRecipients(INSTRUMENT_ID, notificationType, actor.getId()))
        .thenReturn(
            List.of(new BookingNotificationRecipient(recipient, ZoneId.of("Europe/Berlin"))));

    service.notify(booking, actor, notificationType);

    ArgumentCaptor<NotificationConfig> config = ArgumentCaptor.forClass(NotificationConfig.class);
    ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
    verify(communicationManager).notify(eq(actor), isNull(), config.capture(), message.capture());

    assertEquals(notificationType, config.getValue().getNotificationType());
    assertEquals(true, config.getValue().isBroadcast());
    assertEquals(CommunicationNotifyPolicy.ALWAYS_NOTIFY, config.getValue().getPolicyOverride());
    assertEquals(false, config.getValue().isRecordAuthorisationRequired());
    assertEquals(java.util.Set.of(recipient), config.getValue().getNotificationTargetsOverride());
    // CommunicationManager mutates this set before persisting recipients.
    config.getValue().getNotificationTargetsOverride().remove(actor);
    BookingNotificationData data =
        (BookingNotificationData) config.getValue().getNotificationData();
    assertEquals("42", data.getBookingId());
    assertEquals("<Mic & scope>", data.getInstrumentName());
    assertEquals("IN12", data.getInstrumentGlobalIdentifier());
    assertEquals("2026-01-02T03:04:05Z", data.getStartTime());
    assertEquals("2026-01-02T04:04:05Z", data.getEndTime());
    String expectedMessage =
        notificationType == NotificationType.NOTIFICATION_BOOKING_CREATED
            ? "Booking 42 was created for instrument &lt;Mic &amp; scope&gt; (IN12) from "
                + "Jan 2, 2026, 4:04 AM (Europe/Berlin, UTC+01:00) to "
                + "Jan 2, 2026, 5:04 AM (Europe/Berlin, UTC+01:00)."
            : "Booking 42 for instrument &lt;Mic &amp; scope&gt; (IN12) was cancelled. It was "
                + "scheduled from Jan 2, 2026, 4:04 AM (Europe/Berlin, UTC+01:00) to "
                + "Jan 2, 2026, 5:04 AM (Europe/Berlin, UTC+01:00).";
    assertEquals(expectedMessage, message.getValue());
  }

  @Test
  void sendsSeparateMessagesUsingEachSelectedRecipientsZone() {
    User actor = user(1L, "actor");
    User berlinRecipient = user(2L, "berlinRecipient");
    User losAngelesRecipient = user(3L, "losAngelesRecipient");
    when(instrumentDao.getSafeNull(INSTRUMENT_ID))
        .thenReturn(Optional.of(instrument("Microscope")));
    when(recipientReader.selectRecipients(
            INSTRUMENT_ID, NotificationType.NOTIFICATION_BOOKING_CREATED, actor.getId()))
        .thenReturn(
            List.of(
                new BookingNotificationRecipient(berlinRecipient, ZoneId.of("Europe/Berlin")),
                new BookingNotificationRecipient(
                    losAngelesRecipient, ZoneId.of("America/Los_Angeles"))));

    service.notify(
        booking(BOOKING_ID, BookingEventKind.BOOKING, actor),
        actor,
        NotificationType.NOTIFICATION_BOOKING_CREATED);

    ArgumentCaptor<NotificationConfig> configs = ArgumentCaptor.forClass(NotificationConfig.class);
    ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
    verify(communicationManager, org.mockito.Mockito.times(2))
        .notify(eq(actor), isNull(), configs.capture(), messages.capture());
    assertEquals(
        java.util.Set.of(berlinRecipient),
        configs.getAllValues().get(0).getNotificationTargetsOverride());
    assertEquals(
        java.util.Set.of(losAngelesRecipient),
        configs.getAllValues().get(1).getNotificationTargetsOverride());
    assertTrue(messages.getAllValues().get(0).contains("Jan 2, 2026, 4:04 AM"));
    assertTrue(messages.getAllValues().get(0).contains("Europe/Berlin, UTC+01:00"));
    assertTrue(messages.getAllValues().get(1).contains("Jan 1, 2026, 7:04 PM"));
    assertTrue(messages.getAllValues().get(1).contains("America/Los_Angeles, UTC-08:00"));
  }

  @Test
  void doesNotCreateNotificationsWhenTheSnapshotHasNoEligibleRecipients() {
    User actor = user(1L, "actor");
    when(instrumentDao.getSafeNull(INSTRUMENT_ID))
        .thenReturn(Optional.of(instrument("Microscope")));
    when(recipientReader.selectRecipients(
            INSTRUMENT_ID, NotificationType.NOTIFICATION_BOOKING_CREATED, actor.getId()))
        .thenReturn(List.of());

    service.notify(
        booking(BOOKING_ID, BookingEventKind.BOOKING, actor),
        actor,
        NotificationType.NOTIFICATION_BOOKING_CREATED);

    verify(communicationManager, never()).notify(any(), any(), any(), any());
  }

  @Test
  void skipsMaintenanceBookingsAndMissingOrDeletedInstruments() {
    User actor = user(1L, "actor");
    Instrument instrument = instrument("Microscope");
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

    verify(recipientReader, never()).selectRecipients(any(), any(), any());
    verify(communicationManager, never()).notify(any(), any(), any(), any());
  }

  private static User user(long id, String username) {
    User user = TestFactory.createAnyUser(username);
    user.setId(id);
    return user;
  }

  private static Instrument instrument(String name) {
    Instrument instrument = new Instrument();
    instrument.setId(INSTRUMENT_ID);
    instrument.setName(name);
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
