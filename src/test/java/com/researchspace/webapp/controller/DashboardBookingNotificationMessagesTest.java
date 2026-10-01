package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.service.BookingDisplayPreferencesManager;
import com.researchspace.booking.service.BookingNotificationMessageFormatter;
import com.researchspace.model.User;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.comms.data.BookingNotificationData;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.UserManager;
import com.researchspace.session.SessionAttributeUtils;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class DashboardBookingNotificationMessagesTest {

  @AfterEach
  void resetLocale() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  void rendersForCurrentRecipientAndLeavesStoredMessageUntouched() {
    LocaleContextHolder.setLocale(Locale.US);
    DashboardController controller = new DashboardController();
    UserManager userManager = mock(UserManager.class);
    User recipient = mock(User.class);
    BookingDisplayPreferencesManager preferences = mock(BookingDisplayPreferencesManager.class);
    HttpSession session = mock(HttpSession.class);
    Notification structured =
        bookingNotification(
            1L, "Stored email text", data("2026-01-02T03:04:05Z", "2026-01-02T04:04:05Z"));
    Notification legacy =
        bookingNotification(
            2L,
            "Booking 42 was created for instrument Scope 2026-01-02T00:00:00Z (IN12) from "
                + "2026-01-02T03:04:05Z to 2026-01-02T04:04:05Z.",
            null);
    Notification other = new Notification();
    other.setId(3L);
    other.setNotificationType(NotificationType.PROCESS_COMPLETED);

    controller.setUserManager(userManager);
    ReflectionTestUtils.setField(controller, "bookingDisplayPreferences", preferences);
    ReflectionTestUtils.setField(
        controller,
        "bookingMessageFormatter",
        new BookingNotificationMessageFormatter(new JsonMessageSource()));
    ReflectionTestUtils.setField(
        controller, "institutionClock", Clock.fixed(Instant.EPOCH, ZoneId.of("Europe/Berlin")));
    when(userManager.getUserByUsername("recipient")).thenReturn(recipient);
    when(preferences.getForNotificationRecipient(recipient))
        .thenReturn(
            Optional.of(
                new BookingDisplayPreferencesManager.ResolvedBookingDisplayPreferences(
                    "08:00",
                    "18:00",
                    com.researchspace.model.booking.BookingTimezoneMode.CUSTOM,
                    "America/Los_Angeles",
                    "Europe/Berlin",
                    true)));
    when(session.getAttribute(SessionAttributeUtils.TIMEZONE))
        .thenReturn(TimeZone.getTimeZone("Asia/Tokyo"));

    Map<Long, String> messages =
        controller.bookingNotificationMessages(
            List.of(structured, legacy, other), "recipient", session);

    assertEquals("Stored email text", structured.getNotificationMessage());
    assertTrue(messages.get(1L).contains("Jan 1, 2026, 7:04 PM (America/Los_Angeles, UTC-08:00)"));
    assertTrue(messages.get(2L).contains("Scope 2026-01-02T00:00:00Z (IN12)"));
    assertTrue(messages.get(2L).contains("America/Los_Angeles, UTC-08:00"));
    assertFalse(messages.containsKey(3L));
    verify(preferences).getForNotificationRecipient(recipient);
  }

  @Test
  void malformedStructuredDataFallsBackToTheLegacyInterval() {
    DashboardController controller = new DashboardController();
    UserManager userManager = mock(UserManager.class);
    User recipient = mock(User.class);
    BookingDisplayPreferencesManager preferences = mock(BookingDisplayPreferencesManager.class);
    HttpSession session = mock(HttpSession.class);
    Notification malformed =
        bookingNotification(
            4L, "Booking 4 from 2026-01-02T03:04:05Z to 2026-01-02T04:04:05Z.", null);
    malformed.setNotificationData("{broken-json");

    controller.setUserManager(userManager);
    ReflectionTestUtils.setField(controller, "bookingDisplayPreferences", preferences);
    ReflectionTestUtils.setField(
        controller,
        "bookingMessageFormatter",
        new BookingNotificationMessageFormatter(new JsonMessageSource()));
    ReflectionTestUtils.setField(
        controller, "institutionClock", Clock.fixed(Instant.EPOCH, ZoneId.of("Europe/Berlin")));
    when(userManager.getUserByUsername("recipient")).thenReturn(recipient);
    when(preferences.getForNotificationRecipient(recipient)).thenReturn(Optional.empty());

    Map<Long, String> messages =
        controller.bookingNotificationMessages(List.of(malformed), "recipient", session);

    assertTrue(messages.get(4L).contains("Europe/Berlin, UTC+01:00"));
  }

  private static Notification bookingNotification(
      long id, String message, BookingNotificationData data) {
    Notification notification = new Notification();
    notification.setId(id);
    notification.setNotificationType(NotificationType.NOTIFICATION_BOOKING_CREATED);
    notification.setNotificationMessage(message);
    notification.setNotificationDataObject(data);
    return notification;
  }

  private static BookingNotificationData data(String start, String end) {
    BookingNotificationData data = new BookingNotificationData();
    data.setBookingId("42");
    data.setInstrumentName("Microscope");
    data.setInstrumentGlobalIdentifier("IN12");
    data.setStartTime(start);
    data.setEndTime(end);
    return data;
  }
}
