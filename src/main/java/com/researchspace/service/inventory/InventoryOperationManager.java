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
 * join (see adr/0001).
 */
public interface InventoryOperationManager {

  /**
   * @return the newly created sample (with its subsamples), as returned by the sample-creation
   *     manager, or {@code null} for a terminal operation that creates nothing (noOutput, e.g.
   *     Destroy, which only acts on its origins). See adr/0008.
   */
  ApiSampleWithFullSubSamples performOperation(ApiInventoryOperationPost request, User user);
}
