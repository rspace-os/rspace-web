package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.booking.service.BookingCalendarFeedGenerator.CalendarTooLargeException;
import com.researchspace.booking.service.TimeSlotBookingManager.CalendarEvent;
import com.researchspace.booking.service.TimeSlotBookingManager.CalendarSource;
import com.researchspace.model.booking.BookingPrivacy;
import com.researchspace.service.MessageSourceUtils;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookingCalendarFeedGeneratorTest {

  private static final URI SERVER = URI.create("https://rspace.example.com");
  private final MessageSourceUtils messages = mock(MessageSourceUtils.class);
  private final BookingCalendarFeedGenerator generator = new BookingCalendarFeedGenerator(messages);

  @BeforeEach
  void setUp() {
    when(messages.getMessageForLocale("booking:calendar.feed.booked", Locale.ENGLISH))
        .thenReturn("Booked");
    when(messages.getMessageForLocale("booking:calendar.feed.busy", Locale.ENGLISH))
        .thenReturn("Busy");
    when(messages.getMessage(anyString(), any(Object[].class), any(Locale.class)))
        .thenAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              Object[] arguments = invocation.getArgument(1);
              return (key.endsWith("bookedBy") ? "Booked by: " : "Purpose: ") + arguments[0];
            });
  }

  @Test
  void generatesValidDeterministicFullAndBusyEvents() throws Exception {
    CalendarSource source =
        new CalendarSource(
            "Microscope, A;\nRoom",
            "Europe/Berlin",
            List.of(
                event(7L, BookingPrivacy.FULL, "Ada (ada)", "Plate, 4;\\test", true),
                event(8L, BookingPrivacy.BUSY, null, null, false)));

    byte[] first = generator.generate(source, SERVER, Locale.ENGLISH, 100_000);
    byte[] second = generator.generate(source, SERVER, Locale.ENGLISH, 100_000);

    assertArrayEquals(first, second);
    Calendar parsed = new CalendarBuilder().build(new ByteArrayInputStream(first));
    assertFalse(parsed.getProperty(Property.METHOD).isPresent());
    assertEquals(
        "Microscope, A;\nRoom", parsed.getProperty("X-WR-CALNAME").orElseThrow().getValue());
    assertEquals("Europe/Berlin", parsed.getProperty("X-WR-TIMEZONE").orElseThrow().getValue());
    List<VEvent> events = parsed.getComponents(Component.VEVENT);
    assertEquals(2, events.size());

    VEvent full = events.get(0);
    assertEquals(
        "booking-7@rspace.example.com", full.getProperty(Property.UID).orElseThrow().getValue());
    assertEquals("Booked", full.getProperty(Property.SUMMARY).orElseThrow().getValue());
    String description = full.getProperty(Property.DESCRIPTION).orElseThrow().getValue();
    assertTrue(description.contains("Ada (ada)"));
    assertTrue(description.contains("Plate, 4;\\test"));
    assertEquals(
        "https://rspace.example.com/booking/calendar/bookings/7",
        full.getProperty(Property.URL).orElseThrow().getValue());
    assertTrue(full.getProperty(Property.DTSTART).orElseThrow().getValue().endsWith("Z"));
    assertTrue(full.getProperty(Property.DTEND).orElseThrow().getValue().endsWith("Z"));
    assertEquals("CONFIRMED", full.getProperty(Property.STATUS).orElseThrow().getValue());
    assertEquals("OPAQUE", full.getProperty(Property.TRANSP).orElseThrow().getValue());

    VEvent busy = events.get(1);
    assertEquals("Busy", busy.getProperty(Property.SUMMARY).orElseThrow().getValue());
    assertFalse(busy.getProperty(Property.DESCRIPTION).isPresent());
    assertFalse(busy.getProperty(Property.URL).isPresent());
    String busyText = busy.toString();
    assertFalse(busyText.contains("Ada"));
    assertFalse(busyText.contains("Plate"));
    assertFalse(busyText.contains("Booked"));
  }

  @Test
  void acceptsExactByteLimitAndRejectsOneByteLess() {
    CalendarSource source =
        new CalendarSource(
            "Microscope", "UTC", List.of(event(7L, BookingPrivacy.BUSY, null, null, false)));
    byte[] bytes = generator.generate(source, SERVER, Locale.ENGLISH, 100_000);

    assertArrayEquals(bytes, generator.generate(source, SERVER, Locale.ENGLISH, bytes.length));
    assertThrows(
        CalendarTooLargeException.class,
        () -> generator.generate(source, SERVER, Locale.ENGLISH, bytes.length - 1));
  }

  @Test
  void usesBookingUpdateMetadataAndKeepsDownloadedEventEquivalent() throws Exception {
    CalendarEvent event = event(7L, BookingPrivacy.FULL, "Ada (ada)", null, true);
    CalendarSource multi =
        new CalendarSource(
            "Microscope", "UTC", List.of(event, event(8L, BookingPrivacy.BUSY, null, null, false)));
    CalendarSource single = new CalendarSource("Microscope", "UTC", List.of(event));

    VEvent multiEvent =
        new CalendarBuilder()
            .build(
                new ByteArrayInputStream(
                    generator.generate(multi, SERVER, Locale.ENGLISH, 100_000)))
            .<VEvent>getComponents(Component.VEVENT)
            .get(0);
    VEvent singleEvent =
        new CalendarBuilder()
            .build(
                new ByteArrayInputStream(
                    generator.generate(single, SERVER, Locale.ENGLISH, 100_000)))
            .<VEvent>getComponents(Component.VEVENT)
            .get(0);

    assertEquals(multiEvent, singleEvent);
    assertEquals(
        "20260824T120000Z", singleEvent.getProperty(Property.DTSTAMP).orElseThrow().getValue());
    assertEquals(
        "20260824T120000Z",
        singleEvent.getProperty(Property.LAST_MODIFIED).orElseThrow().getValue());
  }

  private static CalendarEvent event(
      Long id, BookingPrivacy privacy, String bookedBy, String purpose, boolean canEdit) {
    return new CalendarEvent(
        id,
        date("2026-08-25T10:00:00Z"),
        date("2026-08-25T11:00:00Z"),
        date("2026-08-20T12:00:00Z"),
        date("2026-08-24T12:00:00Z"),
        privacy,
        bookedBy,
        purpose,
        canEdit);
  }

  private static Date date(String value) {
    return Date.from(Instant.parse(value));
  }
}
