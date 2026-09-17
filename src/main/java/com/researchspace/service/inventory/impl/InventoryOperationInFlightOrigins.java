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
   * <p>This is a judgement, not a proof: {@code perform} declares no transaction timeout, so
   * nothing establishes that every live operation finishes inside the limit. A request still
   * running when its claim is replaced would overlap the request that replaced it. Five minutes is
   * chosen to put that far outside the range any real operation occupies while still releasing an
   * origin orphaned by a crash within one edit-lock lifetime, rather than holding it until the
   * process restarts. Bounding {@code perform} below this limit would turn the judgement into a
   * guarantee.
   */
  static final long STALE_AFTER_MILLIS = Duration.ofMinutes(5).toMillis();

  private record Entry(Object token, long sinceMillis) {}

  private final ConcurrentHashMap<String, Entry> claimed = new ConcurrentHashMap<>();
  private final LongSupplier clock;

  public InventoryOperationInFlightOrigins() {
    this(System::currentTimeMillis);
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
                  entry == null || now - entry.sinceMillis() >= STALE_AFTER_MILLIS
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
    return entry != null && clock.getAsLong() - entry.sinceMillis() < STALE_AFTER_MILLIS;
  }
}
