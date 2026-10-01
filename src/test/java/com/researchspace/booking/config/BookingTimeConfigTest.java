package com.researchspace.booking.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class BookingTimeConfigTest {

  @Test
  void institutionClockUsesTheJvmDefaultTimezone() {
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));

      Clock clock = new BookingTimeConfig().bookingInstitutionClock();

      assertEquals(ZoneId.systemDefault(), clock.getZone());
      assertEquals(ZoneId.of("Pacific/Auckland"), clock.getZone());
    } finally {
      TimeZone.setDefault(original);
    }
  }
}
