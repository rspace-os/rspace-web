package com.researchspace.webapp.controller;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import java.util.Locale;
import java.util.Map;

final class BookingAuditMessageFixture {
  private BookingAuditMessageFixture() {}

  // These labels belong to the frontend audit layer, which is integrated separately.
  static MessageSourceUtils messages() {
    MessageSourceUtils messages = spy(new MessageSourceUtils(new JsonMessageSource()));
    Map.ofEntries(
            Map.entry("allowDoubleBooking", "Allow double booking"),
            Map.entry("bufferAfter", "Buffer after (minutes)"),
            Map.entry("bufferBefore", "Buffer before (minutes)"),
            Map.entry("configuration", "Booking configuration"),
            Map.entry("empty", "No details recorded"),
            Map.entry("enabled", "Enabled"),
            Map.entry("end", "End (UTC)"),
            Map.entry("increment", "Time increment (minutes)"),
            Map.entry("kind", "Event type"),
            Map.entry("maximumDuration", "Maximum duration (minutes)"),
            Map.entry("openingEnd", "Closing time"),
            Map.entry("openingHours", "Opening hours"),
            Map.entry("openingStart", "Opening time"),
            Map.entry("purpose", "Purpose / notes"),
            Map.entry("start", "Start (UTC)"),
            Map.entry("state", "Status"),
            Map.entry("target", "Bookable item"),
            Map.entry("timezone", "Scheduling time zone"))
        .forEach(
            (key, label) ->
                doReturn(label)
                    .when(messages)
                    .getMessageForLocale(
                        "booking:bookableItemDetails.audit.values." + key, Locale.US));
    return messages;
  }
}
