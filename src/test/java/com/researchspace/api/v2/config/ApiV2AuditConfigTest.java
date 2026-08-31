package com.researchspace.api.v2.config;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.researchspace.api.v2.resource.ApiV2AuditLog;
import com.researchspace.api.v2.resource.ApiV2AuditStrictSearch;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

class ApiV2AuditConfigTest {

  @Test
  void auditLogUsesItsUtcClockWhenAnotherClockBeanExists() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.register(ApiV2AuditConfig.class, ApiV2AuditLog.class);
      context.registerBean(
          ApiV2AuditStrictSearch.class, () -> new ApiV2AuditStrictSearch(".", null));
      context.registerBean(
          "bookingInstitutionClock", Clock.class, () -> Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

      context.refresh();

      assertSame(
          context.getBean(ApiV2AuditConfig.AUDIT_CLOCK, Clock.class),
          ReflectionTestUtils.getField(context.getBean(ApiV2AuditLog.class), "clock"));
    }
  }
}
