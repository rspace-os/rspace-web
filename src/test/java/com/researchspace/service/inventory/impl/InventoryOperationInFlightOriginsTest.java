package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.service.inventory.InventoryOperationInProgressException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class InventoryOperationInFlightOriginsTest {

  private final InventoryOperationInFlightOrigins inFlight =
      new InventoryOperationInFlightOrigins();

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
   * A slow request is still a live one. Nothing bounds how long the manager call inside the claim
   * takes, so however long it runs its origins stay its own: no elapsed time frees them, and a
   * repeated attempt is refused every time rather than eventually succeeding (RSDEV-1231).
   */
  @Test
  void aLiveClaimIsNeverStolenHoweverLongItsRequestRuns() {
    InventoryOperationInFlightOrigins.Claim slow = inFlight.claim(List.of("SS1"));

    for (int attempt = 0; attempt < 3; attempt++) {
      assertThrows(
          InventoryOperationInProgressException.class, () -> inFlight.claim(List.of("SS1")));
    }
    assertThrows(InventoryOperationInProgressException.class, () -> inFlight.claim(List.of("SS1")));
    assertTrue(inFlight.isInFlight("SS1"));
    slow.close();
    assertFalse(inFlight.isInFlight("SS1"));
  }

  @Test
  void anEmptyClaimHoldsNothing() {
    inFlight.claim(List.of()).close();
    assertFalse(inFlight.isInFlight("SS1"));
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
