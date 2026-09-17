package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.service.inventory.InventoryOperationInProgressException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class InventoryOperationInFlightOriginsTest {

  private final AtomicLong now = new AtomicLong(1_000_000L);
  private final InventoryOperationInFlightOrigins inFlight =
      new InventoryOperationInFlightOrigins(now::get);

  @Test
  void aFreeOriginIsClaimedAndRefusedToTheNextCallerUntilReleased() {
    InventoryOperationInFlightOrigins.Claim first = inFlight.claim(List.of("SS1"));

    InventoryOperationInProgressException refused =
        assertThrows(
            InventoryOperationInProgressException.class, () -> inFlight.claim(List.of("SS1")));
    assertEquals("SS1", refused.getGlobalId());
    assertTrue(inFlight.isInFlight("SS1"));

    first.close();

    assertFalse(inFlight.isInFlight("SS1"));
    inFlight.claim(List.of("SS1")).close();
  }

  @Test
  void aRefusedMultiOriginClaimHoldsNothing() {
    InventoryOperationInFlightOrigins.Claim other = inFlight.claim(List.of("SS2"));

    InventoryOperationInProgressException refused =
        assertThrows(
            InventoryOperationInProgressException.class,
            () -> inFlight.claim(List.of("SS3", "SS1", "SS2")));

    assertEquals("SS2", refused.getGlobalId());
    assertFalse(inFlight.isInFlight("SS1"), "SS1 was claimed before the refusal and must be freed");
    assertFalse(inFlight.isInFlight("SS3"));
    assertTrue(inFlight.isInFlight("SS2"), "the other request's claim is untouched");
    other.close();
  }

  @Test
  void theSameOriginNamedTwiceInOneRequestIsOneClaim() {
    InventoryOperationInFlightOrigins.Claim claim = inFlight.claim(List.of("SS1", "SS1"));
    assertTrue(inFlight.isInFlight("SS1"));
    claim.close();
    assertFalse(inFlight.isInFlight("SS1"));
  }

  @Test
  void closingTwiceIsHarmlessAndNeverReleasesAnotherRequestsClaim() {
    InventoryOperationInFlightOrigins.Claim first = inFlight.claim(List.of("SS1"));
    first.close();
    InventoryOperationInFlightOrigins.Claim second = inFlight.claim(List.of("SS1"));

    first.close();

    assertTrue(inFlight.isInFlight("SS1"), "the stale handle must not free the live claim");
    second.close();
    assertFalse(inFlight.isInFlight("SS1"));
  }

  /**
   * A claim orphaned by a crash between claim and release must not block the origin forever. The
   * limit is deliberately far longer than any operation runs, so a live request keeps its origins
   * for its whole lifetime; only one that has outlived the limit is replaced (RSDEV-1231).
   */
  @Test
  void aClaimOlderThanTheStaleLimitIsTreatedAsFree() {
    inFlight.claim(List.of("SS1"));
    now.addAndGet(InventoryOperationInFlightOrigins.STALE_AFTER_NANOS - 1);
    assertThrows(InventoryOperationInProgressException.class, () -> inFlight.claim(List.of("SS1")));

    now.addAndGet(1);

    assertFalse(inFlight.isInFlight("SS1"));
    inFlight.claim(List.of("SS1")).close();
  }

  /** The orphan's late release must not free the origin the replacing request now holds. */
  @Test
  void anOrphanedClaimReleasedAfterBeingReplacedLeavesTheReplacementStanding() {
    InventoryOperationInFlightOrigins.Claim orphan = inFlight.claim(List.of("SS1"));
    now.addAndGet(InventoryOperationInFlightOrigins.STALE_AFTER_NANOS);
    InventoryOperationInFlightOrigins.Claim replacement = inFlight.claim(List.of("SS1"));

    orphan.close();

    assertTrue(inFlight.isInFlight("SS1"));
    replacement.close();
    assertFalse(inFlight.isInFlight("SS1"));
  }

  @Test
  void anEmptyClaimHoldsNothing() {
    inFlight.claim(List.of()).close();
    assertFalse(inFlight.isInFlight("SS1"));
  }

  /**
   * The default clock measures elapsed time, so no calendar adjustment can age a live claim: a
   * wall-clock source would let one NTP step forward cross the limit in an instant and hand a
   * running request's origins away (RSDEV-1231).
   */
  @Test
  void theDefaultClockIsMonotonicElapsedTime() {
    InventoryOperationInFlightOrigins elapsed = new InventoryOperationInFlightOrigins();
    elapsed.claim(List.of("SS1"));
    assertTrue(elapsed.isInFlight("SS1"));
    assertTrue(
        InventoryOperationInFlightOrigins.STALE_AFTER_NANOS
            > Duration.ofMinutes(8).toNanos() + Duration.ofSeconds(9).toNanos(),
        "the limit must be expressed in the same unit the clock reports");
  }

  /**
   * The outcome is fixed whatever the thread interleaving: the claim is a single atomic map
   * operation, so of N simultaneous claims on one origin exactly one succeeds and N-1 are refused.
   */
  @Test
  void ofManySimultaneousClaimsOnOneOriginExactlyOneWins() throws Exception {
    int threads = 16;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      Callable<Boolean> attempt =
          () -> {
            start.await();
            try {
              inFlight.claim(List.of("SS1"));
              return true;
            } catch (InventoryOperationInProgressException refused) {
              return false;
            }
          };
      List<Future<Boolean>> results = new java.util.ArrayList<>();
      for (int i = 0; i < threads; i++) {
        results.add(pool.submit(attempt));
      }
      start.countDown();
      int wins = 0;
      for (Future<Boolean> result : results) {
        if (result.get()) {
          wins++;
        }
      }
      assertEquals(1, wins);
    } finally {
      pool.shutdownNow();
    }
  }
}
