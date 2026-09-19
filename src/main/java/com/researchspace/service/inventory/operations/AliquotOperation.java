package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import org.springframework.stereotype.Component;

/** Aliquot: splits part of one subsample into a new sample that is part of the original. */
@Component
public class AliquotOperation extends CreatingOperation<ApiInventoryOperationRequests.Aliquot> {

  @Override
  public String key() {
    return "aliquot";
  }

  @Override
  protected String linkRelation() {
    return "IsPartOf";
  }

  @Override
  protected String linkFieldNameKey() {
    return "operations.aliquot.linkFieldName";
  }
}
