package com.researchspace.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
  void rejectsImmediatelyWhenAllSlotsAreBusyAndReleasesTheSlot() {
    limiter.run(
        () -> {
          ConversionException error =
              assertThrows(ConversionException.class, () -> limiter.run(() -> null));
          assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.status());
          assertEquals(ConversionError.SERVICE_BUSY, error.error());
          assertEquals(1, metrics.counter("rspace.conversion.office.rejected").count());
          assertEquals(1, metrics.get("rspace.conversion.office.active").gauge().value());
          return null;
        });

    assertEquals("available", limiter.run(() -> "available"));
    assertEquals(0, metrics.get("rspace.conversion.office.active").gauge().value());
  }

  @Test
  void rejectsNonPositiveCapacity() {
    assertThrows(IllegalArgumentException.class, () -> properties(0));
  }

  @Test
  void releasesSlotWhenConversionFails() {
    assertThrows(
        IllegalStateException.class,
        () ->
            limiter.run(
                () -> {
                  throw new IllegalStateException("failed");
                }));

    assertEquals("available", limiter.run(() -> "available"));
  }

  private static ConverterProperties properties(int capacity) {
    return new ConverterProperties(
        Path.of("/office"),
        Path.of("/tmp"),
        Duration.ofSeconds(1),
        capacity,
        1024,
        Path.of("/bin/true"),
        Path.of("/tmp/ipc"));
  }
}
