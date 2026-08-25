package com.researchspace.conversion;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ConcurrentHashMap;
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
    int deploymentCapacity = properties.maxConcurrentOfficeConversionsPerDeployment();
    word = new RoleLimiter(capacity, deploymentCapacity, "office", "LibreOffice", meterRegistry);
    pdf = new RoleLimiter(capacity, deploymentCapacity, "pdf", "PDF", meterRegistry);
  }

  <T> T runWord(String deploymentId, Supplier<T> conversion) {
    return word.run(deploymentId, conversion);
  }

  <T> T runPdf(String deploymentId, Supplier<T> conversion) {
    return pdf.run(deploymentId, conversion);
  }

  private static final class RoleLimiter {

    private final Semaphore slots;
    private final int deploymentCapacity;
    private final ConcurrentHashMap<String, Semaphore> deploymentSlots = new ConcurrentHashMap<>();
    private final AtomicInteger active = new AtomicInteger();
    private final Counter rejected;
    private final String displayName;

    private RoleLimiter(
        int capacity,
        int deploymentCapacity,
        String metricRole,
        String displayName,
        MeterRegistry meterRegistry) {
      this.slots = new Semaphore(capacity);
      this.deploymentCapacity = deploymentCapacity;
      this.displayName = displayName;
      rejected =
          Counter.builder("rspace.conversion." + metricRole + ".rejected")
              .description(displayName + " conversions rejected because all slots were busy")
              .register(meterRegistry);
      Gauge.builder("rspace.conversion." + metricRole + ".active", active, AtomicInteger::get)
          .description(displayName + " conversions currently holding a slot")
          .register(meterRegistry);
    }

    private <T> T run(String deploymentId, Supplier<T> conversion) {
      Semaphore deployment =
          deploymentSlots.computeIfAbsent(
              deploymentId, ignored -> new Semaphore(deploymentCapacity));
      if (!deployment.tryAcquire()) {
        reject();
      }
      if (!slots.tryAcquire()) {
        deployment.release();
        reject();
      }
      active.incrementAndGet();
      try {
        return conversion.get();
      } finally {
        active.decrementAndGet();
        slots.release();
        deployment.release();
      }
    }

    private void reject() {
      rejected.increment();
      throw new ConversionException(
          HttpStatus.TOO_MANY_REQUESTS,
          ConversionError.SERVICE_BUSY,
          "All " + displayName + " conversion slots are busy");
    }
  }
}
