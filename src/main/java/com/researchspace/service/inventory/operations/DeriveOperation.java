package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Derive: makes a new sample from part of one subsample by a named process. */
@Component
public class DeriveOperation extends CreatingOperation<ApiInventoryOperationRequests.Derive> {

  @Override
  public String key() {
    return "derive";
  }

  @Override
  protected String linkRelation() {
    return "IsDerivedFrom";
  }

  @Override
  protected String linkFieldNameKey() {
    return "operations.derive.linkFieldName";
  }

  @Override
  protected Map<String, Object> linkFieldNameArgs(ApiInventoryOperationRequests.Derive request) {
    return Map.of("processName", request.getProcessName());
  }
}
