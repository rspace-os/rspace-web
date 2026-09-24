package com.researchspace.service.inventory.impl;

import com.researchspace.service.inventory.InventoryOperationInProgressException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * The origins an Inventory operation request is acting on right now: a second request naming any of
 * them is refused until the first has finished, whoever sent it. Presence is the whole rule and
 * nothing expires a claim on age; see DevDocs/adr/0011 for why.
 */
@Service("inventoryOperationInFlightOrigins")
public class InventoryOperationInFlightOrigins {

  private final ConcurrentHashMap<String, Object> claimed = new ConcurrentHashMap<>();

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
   * Claims every origin or none, ascending, so two requests over overlapping sets refuse each other
   * predictably rather than each holding half.
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

  public boolean isInFlight(String globalId) {
    return claimed.containsKey(globalId);
  }
}
