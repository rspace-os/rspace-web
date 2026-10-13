package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import java.util.List;

/**
 * One origin subsample as the operation sees it, snapshotted by the manager before anything is
 * written.
 */
public record OriginState(
    Long id,
    String globalId,
    String name,
    ApiQuantityInfo quantity,
    List<ParentField> parentSampleFields) {

  public OriginState {
    parentSampleFields = List.copyOf(parentSampleFields);
  }

  public record ParentField(String name, String content, String operationFieldKey) {}
}
