/**
 * RSpace Inventory API Access your RSpace Inventory programmatically. All requests require
 * authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiInventoryEditLock;
import com.researchspace.model.User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/inventory/v1/editLocks")
public interface InventoryEditLocksApi {

  @PostMapping(value = "/{globalId}")
  ApiInventoryEditLock lockItemForEdit(String globalId, User user);

  @DeleteMapping(value = "/{globalId}")
  void unlockItemAfterEdit(String globalId, User user);
}
