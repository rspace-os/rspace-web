package com.researchspace.service.inventory.impl;

import com.researchspace.service.inventory.InventoryOperationInProgressException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Service;

/**
 * The origins an Inventory operation request is acting on right now. A second request naming any of
 * them is refused until the first has finished, whoever sent it: this is what stops two overlapping
 * requests from one user each decrementing an origin from the same read (RSDEV-1231). The
 * edit-session lock cannot, because a re-lock by the same user is an extension.
 *
 * <p>Presence is the whole rule. A request claims its origins before its transaction and releases
 * them after it commits, so an origin is claimed exactly while a write to it may be in flight. The
 * timestamp only frees an origin orphaned by a crash between claim and release. Process-local, like
 * the edit lock.
 */
@Service("inventoryOperationInFlightOrigins")
public class InventoryOperationInFlightOrigins {

  /**
   * A claim this old is taken to have been orphaned, and the next request replaces it.
   *
   * <p>Ten seconds past the transaction timeout on {@code performBiobankOperation}, which is what
   * makes this safe rather than merely likely: a live operation is rolled back at eight minutes, so
   * no request can still be writing when its claim is released at eight minutes ten. The margin
   * absorbs the rollback and the release itself. Change one and the other has to move with it;
   * {@code InventoryOperationTransactionRuleTest} fails if the ordering is ever broken.
   */
  public static final long STALE_AFTER_NANOS = Duration.ofMinutes(8).plusSeconds(10).toNanos();

  private record Entry(Object token, long sinceNanos) {}

  private final ConcurrentHashMap<String, Entry> claimed = new ConcurrentHashMap<>();

  /**
   * Elapsed time, not wall time. {@link System#nanoTime()} has no relation to the calendar and no
   * adjustment moves it, so a clock correction cannot age a live claim past the limit in one step.
   * Its absolute value is meaningless, so only differences are ever compared.
   */
  private final LongSupplier clock;

  public InventoryOperationInFlightOrigins() {
    this(System::nanoTime);
  }

  InventoryOperationInFlightOrigins(LongSupplier clock) {
    this.clock = clock;
  }

  /** The origins one request holds; closing it releases exactly those, and only its own. */
  public final class Claim implements AutoCloseable {
    private final Object token = new Object();
    private final List<String> held = new ArrayList<>();

    private Claim() {}

    @Override
    public void close() {
      for (String globalId : held) {
        claimed.computeIfPresent(globalId, (id, entry) -> entry.token() == token ? null : entry);
      }
      held.clear();
    }
  }

  /**
   * Claims every origin or none. Ascending order, so two requests over overlapping sets refuse each
   * other predictably rather than each holding half.
   *
   * @throws InventoryOperationInProgressException naming the first origin another request holds
   */
  public Claim claim(Collection<String> originGlobalIds) {
    Claim claim = new Claim();
    long now = clock.getAsLong();
    for (String globalId : new TreeSet<>(originGlobalIds)) {
      Entry winner =
          claimed.compute(
              globalId,
              (id, entry) ->
                  entry == null || now - entry.sinceNanos() >= STALE_AFTER_NANOS
                      ? new Entry(claim.token, now)
                      : entry);
      if (winner.token() != claim.token) {
        claim.close();
        throw new InventoryOperationInProgressException(globalId);
      }
      claim.held.add(globalId);
    }
    return claim;
  }

  /** Whether a live claim holds this origin. */
  public boolean isInFlight(String globalId) {
    Entry entry = claimed.get(globalId);
    return entry != null && clock.getAsLong() - entry.sinceNanos() < STALE_AFTER_NANOS;
  }
}
