package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import org.springframework.stereotype.Component;

/**
 * Derive: makes a new sample from part of one subsample by a named process. The process name is
 * what the user is asked to record about the transformation; the sample itself is created exactly
 * as any other derived sample is.
 */
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
}
