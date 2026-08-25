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
    limiter.runWord(
        "deployment-a",
        () -> {
          ConversionException error =
              assertThrows(
                  ConversionException.class, () -> limiter.runWord("deployment-a", () -> null));
          assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.status());
          assertEquals(ConversionError.SERVICE_BUSY, error.error());
          assertEquals(1, metrics.counter("rspace.conversion.office.rejected").count());
          assertEquals(1, metrics.get("rspace.conversion.office.active").gauge().value());
          return null;
        });

    assertEquals("available", limiter.runWord("deployment-a", () -> "available"));
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
            limiter.runWord(
                "deployment-a",
                () -> {
                  throw new IllegalStateException("failed");
                }));

    assertEquals("available", limiter.runWord("deployment-a", () -> "available"));
  }

  @Test
  void pdfAndWordUseIndependentSlots() {
    limiter.runPdf(
        "deployment-a",
        () -> {
          assertEquals("word", limiter.runWord("deployment-a", () -> "word"));
          assertThrows(ConversionException.class, () -> limiter.runPdf("deployment-a", () -> null));
          return null;
        });
  }

  @Test
  void deploymentLimitDoesNotConsumeAnotherDeploymentsSlot() {
    OfficeConversionLimiter twoGlobalSlots =
        new OfficeConversionLimiter(properties(2), new SimpleMeterRegistry());

    twoGlobalSlots.runWord(
        "deployment-a",
        () -> {
          assertThrows(
              ConversionException.class, () -> twoGlobalSlots.runWord("deployment-a", () -> null));
          assertEquals("other", twoGlobalSlots.runWord("deployment-b", () -> "other"));
          return null;
        });
  }

  private static ConverterProperties properties(int capacity) {
    return new ConverterProperties(
        Path.of("/office"),
        Path.of("/tmp"),
        Duration.ofSeconds(1),
        capacity,
        1,
        1024,
        Path.of("/bin/true"),
        Path.of("/credentials"));
  }
}
