package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;

/**
 * Coordinates a configured Inventory operation as a single atomic unit: creates the new sample
 * (with its subsamples, custom fields and relation links) and sets each origin subsample's
 * quantity, rolling everything back on any failure.
 *
 * <p>This is generic. The effect is described entirely by the request; there is no per-operation
 * logic here, so a new operation is a new {@code operations_config.json} entry rather than new
 * Java. Transactionality comes from the {@code service.inventory.*Manager} AOP advice (see {@code
 * applicationContext-service.xml}), whose pointcut matches this interface - it is in {@code
 * service.inventory} and named {@code *Manager}. Calls through it, including the implementation in
 * {@code service.inventory.impl}, run in one transaction that the coordinated sub-manager calls
 * join (see DevDocs/adr/0006).
 */
public interface InventoryOperationManager {

  /**
   * Precondition: the request must already have passed the structural validation the operations
   * endpoint applies (InventoryOperationPostValidator): every origin carries a non-null id and a
   * non-null, unit-bearing amountTaken, and origin ids are unique. The implementation dereferences
   * these without guards, so a future non-controller caller that skips validation would fail
   * mid-transaction instead of cleanly.
   *
   * @return the newly created sample (with its subsamples), as returned by the sample-creation
   *     manager, or {@code null} for a terminal operation that creates nothing (noOutput, e.g.
   *     Destroy, which only acts on its origins). See DevDocs/adr/0013.
   */
  ApiSampleWithFullSubSamples performOperation(ApiInventoryOperationPost request, User user);
}
