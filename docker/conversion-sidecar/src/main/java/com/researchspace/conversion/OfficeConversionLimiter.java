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

  private final Semaphore slots;
  private final AtomicInteger active = new AtomicInteger();
  private final Counter rejected;

  OfficeConversionLimiter(ConverterProperties properties, MeterRegistry meterRegistry) {
    slots = new Semaphore(properties.maxConcurrentOfficeConversions());
    rejected =
        Counter.builder("rspace.conversion.office.rejected")
            .description("LibreOffice conversions rejected because all process slots were busy")
            .register(meterRegistry);
    Gauge.builder("rspace.conversion.office.active", active, AtomicInteger::get)
        .description("LibreOffice conversions currently holding a process slot")
        .register(meterRegistry);
  }

  <T> T run(Supplier<T> conversion) {
    if (!slots.tryAcquire()) {
      rejected.increment();
      throw new ConversionException(
          HttpStatus.TOO_MANY_REQUESTS,
          ConversionError.SERVICE_BUSY,
          "All LibreOffice conversion slots are busy");
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
