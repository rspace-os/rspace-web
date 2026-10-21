package com.researchspace.conversion;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ConversionDeadlineTest {
  @Test
  void interruptsBlockedWorkAndMapsItToTimeoutWithoutLeakingTheInterrupt() {
    try (var deadline = new ConversionDeadline(Duration.ofMillis(50))) {
      assertThrows(InterruptedException.class, () -> new CountDownLatch(1).await());
      assertEquals(
          HttpStatus.GATEWAY_TIMEOUT,
          assertThrows(ConversionException.class, deadline::check).status());
    }
    assertFalse(Thread.currentThread().isInterrupted());
  }
}
