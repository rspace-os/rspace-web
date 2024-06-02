package com.researchspace.service.inventory;

import com.researchspace.api.v1.controller.InventoryBulkOperationsApiController.InventoryBulkOperationConfig;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;

/**
 * For handling bulk API Inventory actions in one transaction, which allows for 'rollbackOnError'
 * behaviour.
 */
public interface InventoryBulkOperationApiManager {

  ApiInventoryBulkOperationResult runBulkOperation(InventoryBulkOperationConfig bulkOpConfig);
}
