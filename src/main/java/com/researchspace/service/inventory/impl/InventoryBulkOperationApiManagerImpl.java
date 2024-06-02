package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.controller.InventoryBulkOperationsApiController.InventoryBulkOperationConfig;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.service.inventory.InventoryBulkOperationApiManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("inventoryBulkOperationApiManager")
public class InventoryBulkOperationApiManagerImpl implements InventoryBulkOperationApiManager {

  private @Autowired InventoryBulkOperationHandler bulkOperationHandler;

  @Override
  public ApiInventoryBulkOperationResult runBulkOperation(
      InventoryBulkOperationConfig bulkOpConfig) {
    return bulkOperationHandler.runBulkOperation(bulkOpConfig);
  }
}
