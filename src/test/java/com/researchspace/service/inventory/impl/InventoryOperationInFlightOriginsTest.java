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

  @Test
  void anEmptyClaimHoldsNothing() {
    inFlight.claim(List.of()).close();
    assertFalse(inFlight.isInFlight("SS1"));
  }

  /**
   * The claim is a single atomic map operation, so the outcome is fixed whatever the interleaving.
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
