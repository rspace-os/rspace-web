package com.researchspace.conversion;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/** One deadline covering validation, startup, conversion, and response transfer for a request. */
final class ConversionDeadline implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(ConversionDeadline.class);
  private static final ScheduledExecutorService TIMER =
      Executors.newScheduledThreadPool(2, Thread.ofPlatform().daemon().factory());

  private Thread owner = Thread.currentThread();
  private final ScheduledFuture<?> timer;
  private final long expiresAt;
  private boolean expired;
  private boolean closed;
  private Runnable abort = () -> {};
  private CompletableFuture<Void> cleanup = CompletableFuture.completedFuture(null);

  ConversionDeadline(Duration timeout) {
    expiresAt = System.nanoTime() + timeout.toNanos();
    timer = TIMER.schedule(this::expire, timeout.toNanos(), TimeUnit.NANOSECONDS);
  }

  private void expire() {
    Runnable action;
    synchronized (this) {
      if (closed || expired) {
        return;
      }
      expired = true;
      if (owner != null) {
        owner.interrupt();
      }
      action = abort;
    }
    Thread.startVirtualThread(action);
  }

  synchronized boolean isExpired() {
    return expired || System.nanoTime() - expiresAt >= 0;
  }

  void abortOnTimeout(Runnable action) {
    boolean alreadyExpired;
    synchronized (this) {
      abort = action;
      alreadyExpired = expired;
    }
    if (alreadyExpired) {
      Thread.startVirtualThread(action);
    }
    check();
  }

  synchronized long remainingNanos() {
    return Math.max(0, expiresAt - System.nanoTime());
  }

  void handoff(Runnable action) {
    synchronized (this) {
      if (!isExpired()) {
        owner = null;
        abort = action;
        return;
      }
    }
    check();
  }

  void resume(Runnable action) {
    synchronized (this) {
      if (!isExpired()) {
        owner = Thread.currentThread();
        abort = action;
        return;
      }
    }
    check();
  }

  synchronized void cleanupBeforeRelease(CompletableFuture<Void> completion) {
    cleanup = CompletableFuture.allOf(cleanup, completion);
  }

  synchronized void releaseAfterCleanup(Runnable release) {
    cleanup.whenComplete(
        (ignored, failure) -> {
          try {
            if (failure != null) {
              LOG.warn("Conversion cleanup failed", failure);
            }
          } finally {
            release.run();
          }
        });
  }

  void check() {
    if (isExpired()) {
      expire();
      throw new ConversionException(
          HttpStatus.GATEWAY_TIMEOUT, ConversionError.TIMEOUT, "Conversion deadline exceeded");
    }
  }

  @Override
  public synchronized void close() {
    closed = true;
    timer.cancel(false);
    if (expired && owner == Thread.currentThread()) {
      // Do not return a request thread to Tomcat with our cancellation interrupt set.
      Thread.interrupted();
    }
    owner = null;
  }
}
