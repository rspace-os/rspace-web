package com.researchspace.conversion;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Bounds the number of LibreOffice processes owned by this sidecar. */
@Component
class OfficeConversionLimiter {

  private final RoleLimiter word;
  private final RoleLimiter pdf;

  OfficeConversionLimiter(ConverterProperties properties, MeterRegistry meterRegistry) {
    int capacity = properties.maxConcurrentOfficeConversions();
    word = new RoleLimiter(capacity, "office", "LibreOffice", meterRegistry);
    pdf = new RoleLimiter(capacity, "pdf", "PDF", meterRegistry);
  }

  Permit acquireWord() {
    return word.acquire();
  }

  Permit acquirePdf() {
    return pdf.acquire();
  }

  boolean hasWordCapacity() {
    return word.hasCapacity();
  }

  boolean hasPdfCapacity() {
    return pdf.hasCapacity();
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

    private Permit acquire() {
      if (!slots.tryAcquire()) {
        reject();
      }
      active.incrementAndGet();
      return new Permit(
          () -> {
            active.decrementAndGet();
            slots.release();
          });
    }

    private boolean hasCapacity() {
      return slots.availablePermits() > 0;
    }

    private void reject() {
      rejected.increment();
      throw new ConversionException(
          HttpStatus.TOO_MANY_REQUESTS,
          ConversionError.SERVICE_BUSY,
          "All " + displayName + " conversion slots are busy");
    }
  }

  static final class Permit implements AutoCloseable {

    private final Runnable release;
    private final AtomicBoolean closed = new AtomicBoolean();

    Permit(Runnable release) {
      this.release = release;
    }

    @Override
    public void close() {
      if (closed.compareAndSet(false, true)) {
        release.run();
      }
    }
  }
}
