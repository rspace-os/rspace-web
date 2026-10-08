package com.researchspace.api.v2.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Time source for UTC-bounded REST API v2 audit snapshots. */
@Configuration
public class ApiV2AuditConfig {

  public static final String AUDIT_CLOCK = "apiV2AuditClock";

  @Bean(AUDIT_CLOCK)
  Clock apiV2AuditClock() {
    return Clock.systemUTC();
  }
}
