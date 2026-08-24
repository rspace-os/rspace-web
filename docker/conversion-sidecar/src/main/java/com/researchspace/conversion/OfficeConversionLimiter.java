package com.researchspace.conversion;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Bounds the number of LibreOffice processes owned by this sidecar. */
@Component
final class OfficeConversionLimiter {

  private final RoleLimiter word;
  private final RoleLimiter pdf;

  OfficeConversionLimiter(ConverterProperties properties, MeterRegistry meterRegistry) {
    int capacity = properties.maxConcurrentOfficeConversions();
    word = new RoleLimiter(capacity, "office", "LibreOffice", meterRegistry);
    pdf = new RoleLimiter(capacity, "pdf", "PDF", meterRegistry);
  }

  <T> T runWord(Supplier<T> conversion) {
    return word.run(conversion);
  }

  <T> T runPdf(Supplier<T> conversion) {
    return pdf.run(conversion);
  }

  private static final class RoleLimiter {

    private final Semaphore slots;
    private final AtomicInteger active = new AtomicInteger();
    private final Counter rejected;
    private final String displayName;

    private RoleLimiter(
        int capacity, String metricRole, String displayName, MeterRegistry meterRegistry) {
      this.slots = new Semaphore(capacity);
      this.displayName = displayName;
      rejected =
          Counter.builder("rspace.conversion." + metricRole + ".rejected")
              .description(displayName + " conversions rejected because all slots were busy")
              .register(meterRegistry);
      Gauge.builder("rspace.conversion." + metricRole + ".active", active, AtomicInteger::get)
          .description(displayName + " conversions currently holding a slot")
          .register(meterRegistry);
    }

    private <T> T run(Supplier<T> conversion) {
      if (!slots.tryAcquire()) {
        rejected.increment();
        throw new ConversionException(
            HttpStatus.TOO_MANY_REQUESTS,
            ConversionError.SERVICE_BUSY,
            "All " + displayName + " conversion slots are busy");
      }
      active.incrementAndGet();
      try {
        return conversion.get();
      } finally {
        active.decrementAndGet();
        slots.release();
      }
    }
  }
}
