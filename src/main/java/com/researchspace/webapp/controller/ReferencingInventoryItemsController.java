package com.researchspace.webapp.controller;

import com.researchspace.api.v1.model.ApiInventoryReferencingItems;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import com.researchspace.service.inventory.InventoryLinkManager;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Session-authenticated lookup of the Inventory items that link to a given ELN record. Backs the
 * ELN-side "Related inventory items" panels (the legacy record info panel for documents/notebooks
 * and the React gallery info panel). Those run in the browser session and do not present the API
 * key or OAuth bearer token that the {@code /api/inventory/v1} endpoints require, so they call this
 * {@code /workspace} endpoint instead. It delegates to the same permission-filtered manager method
 * as the API endpoint, so callers only ever see items they may read.
 */
@Controller
@RequestMapping("/workspace")
public class ReferencingInventoryItemsController {

  @Autowired private InventoryLinkManager inventoryLinkManager;
  @Autowired private UserManager userManager;

  @ResponseBody
  @GetMapping("/getReferencingInventoryItems/{globalId}")
  public ApiInventoryReferencingItems getReferencingInventoryItems(
      @PathVariable("globalId") String globalId, Principal principal) {
    User user = userManager.getUserByUsername(principal.getName());
    ApiInventoryReferencingItems result = new ApiInventoryReferencingItems();
    result.setReferencingItems(inventoryLinkManager.findReferencingItems(globalId, user));
    return result;
  }
}
