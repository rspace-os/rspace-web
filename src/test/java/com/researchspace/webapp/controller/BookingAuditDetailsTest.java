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
import org.junit.jupiter.api.Test;

class BookingAuditDetailsTest {

  private final MessageSourceUtils messages = new MessageSourceUtils(new JsonMessageSource());

  @Test
  void configurationSnapshotReadsAsLabelledOpeningHours() {
    // A logged booking configuration write, as LogLineParser reads it.
    AuditData data =
        AuditData.fromJson(
            "{\"data\":{\"allowDoubleBooking\":false,\"bufferAfterMinutes\":0,"
                + "\"bufferBeforeMinutes\":0,\"enabled\":true,"
                + "\"id\":\"booking-configurations:910100012\",\"maxBookingDurationMinutes\":0,"
                + "\"openDays\":[1,2,4,5,7],\"openingEnd\":\"18:00\","
                + "\"openingExceptions\":[{\"dayOfWeek\":4,\"start\":\"10:00\",\"end\":\"14:00\"}],"
                + "\"openingStart\":\"08:00\",\"slotGranularityMinutes\":5,\"state\":\"ACTIVE\","
                + "\"target\":{\"type\":\"INSTRUMENT\",\"id\":5},\"timezone\":\"Etc/UTC\"}}");

    assertEquals(
        "Status: ACTIVE; Bookable item: IN5; Enabled: true; Scheduling time zone: Etc/UTC;"
            + " Opening time: 08:00; Closing time: 18:00;"
            + " Open days: Monday, Tuesday, Thursday, Friday, Sunday;"
            + " Different hours by day: Thursday 10:00–14:00; Time increment (minutes): 5;"
            + " Maximum duration (minutes): 0; Buffer before (minutes): 0;"
            + " Buffer after (minutes): 0; Allow double booking: false",
        BookingAuditDetails.format(data.getData(), messages, Locale.US));
  }

  @Test
  void closingMidnightReadsAsMidnightAndNoExceptionsAsDash() {
    AuditData allDay =
        AuditData.fromJson(
            "{\"data\":{\"openingStart\":\"00:00\",\"openingEnd\":\"24:00\","
                + "\"openingExceptions\":[]}}");
    AuditData allDayException =
        AuditData.fromJson(
            "{\"data\":{\"openingExceptions\":"
                + "[{\"dayOfWeek\":1,\"start\":\"00:00\",\"end\":\"24:00\"}]}}");

    assertEquals(
        "Opening time: 00:00; Closing time: 00:00; Different hours by day: —",
        BookingAuditDetails.format(allDay.getData(), messages, Locale.US));
    assertEquals(
        "Different hours by day: Monday 00:00–00:00",
        BookingAuditDetails.format(allDayException.getData(), messages, Locale.US));
  }

  @Test
  void bookingSnapshotShowsUtcInstantsAndUnknownKeysRaw() {
    AuditData data =
        AuditData.fromJson(
            "{\"data\":{\"bookingConfigurationId\":\"booking-configurations:12\","
                + "\"end\":1790776800000,\"id\":\"bookings:93\",\"kind\":\"MAINTENANCE\","
                + "\"purpose\":null,\"start\":1790773200000,\"state\":\"CANCELLED\","
                + "\"removedBookings\":2,\"openDays\":[0],\"legacyField\":\"x\"}}");

    assertEquals(
        "Start (UTC): 2026-09-30T13:00:00Z; End (UTC): 2026-09-30T14:00:00Z;"
            + " Event type: MAINTENANCE; Purpose / notes: —; Status: CANCELLED;"
            + " Booking configuration: booking-configurations:12; Open days: [0];"
            + " Removed bookings: 2; legacyField: x",
        BookingAuditDetails.format(data.getData(), messages, Locale.US));
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
  void addsDetailsOnlyToBookingEvents() {
    AuditTrailSearchResult booking = result(AuditDomain.BOOKING, "bookings:1");
    AuditTrailSearchResult legacyBooking = result(AuditDomain.UNKNOWN, "bookings:2");
    AuditTrailSearchResult record = result(AuditDomain.RECORD, "SD3");

    BookingAuditDetails.addTo(List.of(booking, legacyBooking, record), messages, Locale.US);

    assertEquals("Status: ACTIVE", booking.getEvent().getDetails());
    assertEquals("Status: ACTIVE", legacyBooking.getEvent().getDetails());
    assertNull(record.getEvent().getDetails());
  }

  private static AuditTrailSearchResult result(AuditDomain domain, String id) {
    AuditData data = new AuditData();
    data.getData().put("id", id);
    data.getData().put("state", "ACTIVE");
    return new AuditTrailSearchResult(
        new HistoricData(domain, AuditAction.WRITE, "Any User", data, "any"), 0L);
  }
}
