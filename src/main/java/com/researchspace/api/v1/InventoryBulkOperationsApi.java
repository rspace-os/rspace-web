/**
 * RSpace Inventory API Access your RSpace Inventory programmatically. All requests require
 * authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiInventoryBulkOperationPost;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.model.User;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/inventory/v1/bulk")
public interface InventoryBulkOperationsApi {

  @PostMapping
  ApiInventoryBulkOperationResult executeBulkOperation(
      ApiInventoryBulkOperationPost bulkApiRequest, BindingResult errors, User user)
      throws BindException;
}
