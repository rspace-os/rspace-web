package com.researchspace.booking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Validated deployment limits for calendar feed generation. */
@Component
public record BookingCalendarProperties(
    @Value("${booking.calendar.feed.maxEvents:5000}") int maxEvents,
    @Value("${booking.calendar.feed.maxBytes:10485760}") int maxBytes,
    @Value("${booking.calendar.feed.maxConcurrentGenerations:4}") int maxConcurrentGenerations) {

  public BookingCalendarProperties {
    maxEvents = positive(maxEvents, "booking.calendar.feed.maxEvents");
    maxBytes = positive(maxBytes, "booking.calendar.feed.maxBytes");
    maxConcurrentGenerations =
        positive(maxConcurrentGenerations, "booking.calendar.feed.maxConcurrentGenerations");
  }

  private static int positive(int value, String property) {
    if (value < 1) {
      throw new IllegalArgumentException(property + " must be positive");
    }
    return value;
  }
}
