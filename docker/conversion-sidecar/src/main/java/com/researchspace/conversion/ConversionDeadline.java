package com.researchspace.conversion;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;

/** One deadline covering startup, conversion, and output transfer for a request. */
final class ConversionDeadline implements AutoCloseable {
  private static final java.util.concurrent.ScheduledExecutorService TIMER =
      Executors.newScheduledThreadPool(2, Thread.ofPlatform().daemon().factory());

  private final Thread owner = Thread.currentThread();
  private final ScheduledFuture<?> timer;
  private final long expiresAt;
  private boolean expired;
  private boolean closed;
  private Runnable abort = () -> {};

  ConversionDeadline(Duration timeout) {
    expiresAt = System.nanoTime() + timeout.toNanos();
    timer = TIMER.schedule(this::expire, timeout.toNanos(), TimeUnit.NANOSECONDS);
  }

  private synchronized void expire() {
    if (!closed) {
      expired = true;
      owner.interrupt();
      abort.run();
    }
  }

  synchronized void abortOnTimeout(Runnable action) {
    abort = action;
    if (expired) {
      action.run();
      check();
    }
  }

  synchronized void check() {
    if (expired || System.nanoTime() - expiresAt >= 0) {
      throw new ConversionException(
          HttpStatus.GATEWAY_TIMEOUT, ConversionError.TIMEOUT, "Conversion deadline exceeded");
    }
  }

  @Override
  public synchronized void close() {
    closed = true;
    timer.cancel(false);
    if (expired) {
      // Do not return a request thread to Tomcat with our cancellation interrupt set.
      Thread.interrupted();
    }
  }
}
