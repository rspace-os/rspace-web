package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditData;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.HistoricData;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BookingAuditDetailsTest {

  private final MessageSourceUtils messages = new MessageSourceUtils(new JsonMessageSource());

  @Test
  void formatsIndependentHoursAndKeepsUnknownHistoricalSchedulesVisible() {
    AuditData data =
        AuditData.fromJson(
            "{\"data\":{\"openingHours\":["
                + "{\"dayOfWeek\":1,\"start\":\"09:00\",\"end\":\"17:00\"},"
                + "{\"dayOfWeek\":3,\"start\":\"00:00\",\"end\":\"24:00\"}]}}");
    assertEquals(
        "Opening hours: Monday 09:00–17:00, Wednesday 00:00–00:00",
        BookingAuditDetails.format(data.getData(), messages, Locale.US));
    AuditData unknown = AuditData.fromJson("{\"data\":{\"openingHours\":null}}");
    assertEquals(
        "Opening hours: —", BookingAuditDetails.format(unknown.getData(), messages, Locale.US));
  }

  @Test
  void bookingSnapshotShowsUtcInstantsAndUnknownKeysRaw() {
    AuditData data =
        AuditData.fromJson(
            "{\"data\":{\"bookingConfigurationId\":\"booking-configurations:12\","
                + "\"end\":1790776800000,\"id\":\"bookings:93\",\"kind\":\"MAINTENANCE\","
                + "\"purpose\":null,\"start\":1790773200000,\"state\":\"CANCELLED\","
                + "\"removedBookings\":2,\"legacyField\":\"x\"}}");

    assertEquals(
        "Start (UTC): 2026-09-30T13:00:00Z; End (UTC): 2026-09-30T14:00:00Z;"
            + " Event type: MAINTENANCE; Purpose / notes: —; Status: CANCELLED;"
            + " Booking configuration: booking-configurations:12;"
            + " Removed bookings: 2; legacyField: x",
        BookingAuditDetails.format(data.getData(), messages, Locale.US));
  }

  @Test
  void cancellationReasonIsLabelledButNullAndIsoLookingTextStayUnmodified() {
    AuditData withReason =
        AuditData.fromJson(
            "{\"data\":{\"state\":\"CANCELLED\","
                + "\"cancellationReason\":\"2026-09-28T18:29:48.555718112Z\"}}");
    AuditData withoutReason =
        AuditData.fromJson("{\"data\":{\"state\":\"CONFIRMED\",\"cancellationReason\":null}}");

    assertEquals(
        "Status: CANCELLED; Cancellation reason: 2026-09-28T18:29:48.555718112Z",
        BookingAuditDetails.format(withReason.getData(), messages, Locale.US));
    assertEquals(
        "Status: CONFIRMED",
        BookingAuditDetails.format(withoutReason.getData(), messages, Locale.US));
  }

  @Test
  void instantsReadToTheSecond() {
    AuditData data =
        AuditData.fromJson(
            "{\"data\":{\"id\":\"booking-configurations:525\","
                + "\"start\":1790773200123,\"end\":1790776800999,"
                + "\"deletedAt\":\"2026-09-28T18:29:48.555718112Z\","
                + "\"purpose\":\"2026-09-28T18:29:48.5Z\",\"legacyAt\":\"2026-09-28T18:29:48Z\"}}");

    assertEquals(
        "Start (UTC): 2026-09-30T13:00:00Z; End (UTC): 2026-09-30T14:00:00Z;"
            + " Purpose / notes: 2026-09-28T18:29:48.5Z; Deleted at (UTC): 2026-09-28T18:29:48Z;"
            + " legacyAt: 2026-09-28T18:29:48Z",
        BookingAuditDetails.format(data.getData(), messages, Locale.US));
  }

  @Test
  void displayNamePrefersTheRecordedNameThenTheTargetThenTheConfiguration() {
    assertEquals(
        "Acceptance admin instrument",
        BookingAuditDetails.displayName(
            AuditData.fromJson(
                    "{\"data\":{\"id\":\"booking-configurations:525\","
                        + "\"target\":{\"type\":\"INSTRUMENT\",\"id\":591},"
                        + "\"targetName\":\"Acceptance admin instrument\"}}")
                .getData()));
    assertEquals(
        "IN5",
        BookingAuditDetails.displayName(
            AuditData.fromJson(
                    "{\"data\":{\"id\":\"booking-configurations:12\",\"targetName\":\" \","
                        + "\"target\":{\"type\":\"INSTRUMENT\",\"id\":5}}}")
                .getData()));
    assertEquals(
        "booking-configurations:12",
        BookingAuditDetails.displayName(
            AuditData.fromJson(
                    "{\"data\":{\"id\":\"bookings:93\","
                        + "\"bookingConfigurationId\":\"booking-configurations:12\"}}")
                .getData()));
    assertNull(
        BookingAuditDetails.displayName(
            AuditData.fromJson(
                    "{\"data\":{\"id\":\"booking-settings:1\","
                        + "\"target\":{\"type\":\"CONTAINER\",\"id\":5}}}")
                .getData()));
  }

  @Test
  void defaultsSnapshotLabelsItsDisplayFields() {
    AuditData data =
        AuditData.fromJson(
            "{\"data\":{\"id\":\"booking-settings:1\",\"availabilityWindowStart\":\"08:00\","
                + "\"availabilityWindowEnd\":\"18:00\",\"timezoneMode\":\"BROWSER\","
                + "\"customTimezone\":null,\"defaultSharedWith\":\"ALL_USERS\"}}");

    assertEquals(
        "Availability window start: 08:00; Availability window end: 18:00; Time zone mode: BROWSER;"
            + " Custom time zone: —; Default sharing: ALL_USERS",
        BookingAuditDetails.format(data.getData(), messages, Locale.US));
  }

  @Test
  void resolvesCleanupSnapshotLabelsFromTheRealCatalog() {
    AuditData data =
        AuditData.fromJson(
            "{\"data\":{\"targetName\":\"Reader\",\"configurationVersion\":2,"
                + "\"removedBookings\":3,\"removedSubscriptions\":4,"
                + "\"deletedAt\":\"2026-10-17T00:00:00Z\"}}");

    assertEquals(
        "Bookable item name: Reader; Configuration version: 2; Removed bookings: 3; Removed"
            + " subscriptions: 4; Deleted at (UTC): 2026-10-17T00:00:00Z",
        BookingAuditDetails.format(data.getData(), messages, Locale.US));
  }

  @Test
  void addsDetailsOnlyToBookingEvents() {
    AuditTrailSearchResult booking = result(AuditDomain.BOOKING, "bookings:1");
    AuditTrailSearchResult record = result(AuditDomain.RECORD, "SD3");

    booking.getEvent().getData().getData().put("target", Map.of("type", "INSTRUMENT", "id", 7));
    record.getEvent().getData().getData().put("target", Map.of("type", "INSTRUMENT", "id", 7));

    BookingAuditDetails.addTo(List.of(booking, record), messages, Locale.US);

    assertEquals("Status: Active; Bookable item: IN7", booking.getEvent().getDetails());
    assertEquals("IN7", booking.getEvent().getDisplayName());
    assertNull(record.getEvent().getDetails());
    assertNull(record.getEvent().getDisplayName());
  }

  private static AuditTrailSearchResult result(AuditDomain domain, String id) {
    AuditData data = new AuditData();
    data.getData().put("id", id);
    data.getData().put("state", "ACTIVE");
    return new AuditTrailSearchResult(
        new HistoricData(domain, AuditAction.WRITE, "Any User", data, "any"), 0L);
  }
}
