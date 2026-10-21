package com.researchspace.conversion;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

class ConversionDeadlineTest {
  @Test
  void releasesPermitWhenCleanupFails() {
    var released = new AtomicBoolean();
    var cleanup = new CompletableFuture<Void>();
    try (var deadline = new ConversionDeadline(Duration.ofSeconds(5))) {
      deadline.cleanupBeforeRelease(cleanup);
      deadline.releaseAfterCleanup(() -> released.set(true));
      assertFalse(released.get());
      cleanup.completeExceptionally(new UncheckedIOException(new IOException("cleanup failed")));
      assertTrue(released.get());
    }
  }

  @Test
  void blockedAbortActionsDoNotDelayOtherRequestDeadlines() throws Exception {
    var entered = new CountDownLatch(2);
    var unblock = new CountDownLatch(1);
    Runnable blockedAbort =
        () -> {
          entered.countDown();
          try {
            unblock.await();
          } catch (InterruptedException e) {
            LoggerFactory.getLogger(getClass()).warn("Test abort interrupted", e);
            Thread.currentThread().interrupt();
          }
        };
    try (var first = new ConversionDeadline(Duration.ofMillis(100));
        var second = new ConversionDeadline(Duration.ofMillis(100))) {
      first.handoff(blockedAbort);
      second.handoff(blockedAbort);
      try {
        assertTrue(entered.await(1, TimeUnit.SECONDS));
        try (var third = new ConversionDeadline(Duration.ofMillis(50))) {
          assertThrows(
              InterruptedException.class, () -> new CountDownLatch(1).await(1, TimeUnit.SECONDS));
        }
      } finally {
        unblock.countDown();
      }
    }
    assertFalse(Thread.currentThread().isInterrupted());
  }

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
