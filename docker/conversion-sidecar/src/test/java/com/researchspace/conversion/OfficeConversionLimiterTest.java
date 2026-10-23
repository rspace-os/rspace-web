package com.researchspace.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class OfficeConversionLimiterTest {

  private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
  private final OfficeConversionLimiter limiter =
      new OfficeConversionLimiter(properties(1), metrics);

  @Test
  void holdsWordPermitUntilClosedAndThenReleasesIt() {
    OfficeConversionLimiter.Permit permit = limiter.acquireWord();

    assertFalse(limiter.hasWordCapacity());
    ConversionException error = assertThrows(ConversionException.class, limiter::acquireWord);
    assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.status());
    assertEquals(ConversionError.SERVICE_BUSY, error.error());
    assertEquals(1, metrics.counter("rspace.conversion.office.rejected").count());
    assertEquals(1, metrics.get("rspace.conversion.office.active").gauge().value());

    permit.close();
    permit.close();
    assertTrue(limiter.hasWordCapacity());
    assertEquals(0, metrics.get("rspace.conversion.office.active").gauge().value());
  }

  @Test
  void pdfAndWordUseIndependentCapacity() {
    try (OfficeConversionLimiter.Permit ignored = limiter.acquirePdf()) {
      assertFalse(limiter.hasPdfCapacity());
      assertTrue(limiter.hasWordCapacity());
    }
  }

  @Test
  void rejectsNonPositiveCapacity() {
    assertThrows(IllegalArgumentException.class, () -> properties(0));
  }

  private static ConverterProperties properties(int capacity) {
    return new ConverterProperties(
        Path.of("/office"),
        Path.of("/tmp"),
        Duration.ofSeconds(1),
        Duration.ofSeconds(1),
        Duration.ofSeconds(1),
        Duration.ofSeconds(1),
        capacity,
        1024,
        Path.of("/bin/true"));
  }
}
