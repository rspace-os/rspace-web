package com.researchspace.booking.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Time sources used only by Booking display and creation defaults. */
@Configuration(proxyBeanMethods = false)
public class BookingTimeConfig {

  public static final String INSTITUTION_CLOCK = "bookingInstitutionClock";

  @Bean(name = INSTITUTION_CLOCK)
  Clock bookingInstitutionClock() {
    return Clock.systemDefaultZone();
  }
}
