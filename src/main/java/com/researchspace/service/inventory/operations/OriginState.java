package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import java.util.List;

/**
 * One origin subsample as the operation sees it, snapshotted by the manager before anything is
 * written. Everything an operation needs about what it is acting on, and nothing that would let it
 * reach back into persistence.
 */
public record OriginState(
    Long id,
    String globalId,
    String name,
    ApiQuantityInfo quantity,
    List<ParentField> parentSampleFields) {

  public OriginState {
    parentSampleFields = parentSampleFields == null ? List.of() : List.copyOf(parentSampleFields);
  }

  /** A field on the origin's parent sample that an operation may read (Passage's counter). */
  public record ParentField(String name, String content, String operationFieldKey) {}
}
