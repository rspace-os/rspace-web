package com.researchspace.booking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Validated deployment limits for calendar feed generation. */
@Component
public final class BookingCalendarProperties {

  private final int maxEvents;
  private final int maxBytes;
  private final int maxConcurrentGenerations;

  public BookingCalendarProperties(
      @Value("${booking.calendar.feed.maxEvents:5000}") int maxEvents,
      @Value("${booking.calendar.feed.maxBytes:10485760}") int maxBytes,
      @Value("${booking.calendar.feed.maxConcurrentGenerations:4}") int maxConcurrentGenerations) {
    this.maxEvents = positive(maxEvents, "booking.calendar.feed.maxEvents");
    this.maxBytes = positive(maxBytes, "booking.calendar.feed.maxBytes");
    this.maxConcurrentGenerations =
        positive(maxConcurrentGenerations, "booking.calendar.feed.maxConcurrentGenerations");
  }

  public int maxEvents() {
    return maxEvents;
  }

  public int maxBytes() {
    return maxBytes;
  }

  public int maxConcurrentGenerations() {
    return maxConcurrentGenerations;
  }

  private static int positive(int value, String property) {
    if (value < 1) {
      throw new IllegalArgumentException(property + " must be positive");
    }
    return value;
  }
}
