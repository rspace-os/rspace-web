package com.researchspace.service.inventory.impl;

import com.researchspace.service.inventory.InventoryOperationInProgressException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * The origins an Inventory operation request is acting on right now. A second request naming any of
 * them is refused until the first has finished, whoever sent it: this is what stops two overlapping
 * requests from one user each decrementing an origin from the same read (RSDEV-1231). The
 * edit-session lock cannot, because a re-lock by the same user is an extension.
 *
 * <p>Presence is the whole rule, and a claim ends only when its owner releases it. Nothing here
 * expires a claim on age: no transaction timeout bounds the manager call a claim surrounds, so a
 * slow request is indistinguishable from an abandoned one, and guessing would hand a live request's
 * origins to a second one. try-with-resources releases on every exit, normal or exceptional, and a
 * process that dies takes the map with it. Process-local, like the edit lock.
 */
@Service("inventoryOperationInFlightOrigins")
public class InventoryOperationInFlightOrigins {

  private final ConcurrentHashMap<String, Object> claimed = new ConcurrentHashMap<>();

  /** The origins one request holds; closing it releases exactly those, and only its own. */
  public final class Claim implements AutoCloseable {
    private final Object token = new Object();
    private final List<String> held = new ArrayList<>();

    private Claim() {}

    @Override
    public void close() {
      for (String globalId : held) {
        claimed.remove(globalId, token);
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
    for (String globalId : new TreeSet<>(originGlobalIds)) {
      if (claimed.putIfAbsent(globalId, claim.token) != null) {
        claim.close();
        throw new InventoryOperationInProgressException(globalId);
      }
      claim.held.add(globalId);
    }
    return claim;
  }

  /** Whether a live claim holds this origin. */
  public boolean isInFlight(String globalId) {
    return claimed.containsKey(globalId);
  }
}
