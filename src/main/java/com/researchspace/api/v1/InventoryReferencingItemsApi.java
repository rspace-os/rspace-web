/**
 * RSpace Inventory API: back-references for Inventory Link extra-fields. Returns the items whose
 * Link extraField points at the supplied target item.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiInventoryReferencingItems;
import com.researchspace.model.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/inventory/v1")
public interface InventoryReferencingItemsApi {

  @GetMapping("/samples/{id}/referencingItems")
  ApiInventoryReferencingItems getReferencingItemsForSample(@PathVariable Long id, User user);

  @GetMapping("/subSamples/{id}/referencingItems")
  ApiInventoryReferencingItems getReferencingItemsForSubSample(@PathVariable Long id, User user);

  @GetMapping("/containers/{id}/referencingItems")
  ApiInventoryReferencingItems getReferencingItemsForContainer(@PathVariable Long id, User user);

  @GetMapping("/instruments/{id}/referencingItems")
  ApiInventoryReferencingItems getReferencingItemsForInstrument(@PathVariable Long id, User user);
}
