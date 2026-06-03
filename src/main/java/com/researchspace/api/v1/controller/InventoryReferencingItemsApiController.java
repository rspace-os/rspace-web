package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.InventoryReferencingItemsApi;
import com.researchspace.api.v1.model.ApiInventoryReferencingItems;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.service.inventory.InventoryLinkManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;

@ApiController
public class InventoryReferencingItemsApiController implements InventoryReferencingItemsApi {

  @Autowired private InventoryLinkManager inventoryLinkManager;

  @Override
  public ApiInventoryReferencingItems getReferencingItemsForSample(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    return forItem(GlobalIdPrefix.SA, id, user);
  }

  @Override
  public ApiInventoryReferencingItems getReferencingItemsForSubSample(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    return forItem(GlobalIdPrefix.SS, id, user);
  }

  @Override
  public ApiInventoryReferencingItems getReferencingItemsForContainer(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    return forItem(GlobalIdPrefix.IC, id, user);
  }

  @Override
  public ApiInventoryReferencingItems getReferencingItemsForInstrument(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    return forItem(GlobalIdPrefix.IN, id, user);
  }

  @Override
  public ApiInventoryReferencingItems getReferencingItemsForGlobalId(
      @PathVariable String globalId, @RequestAttribute(name = "user") User user) {
    ApiInventoryReferencingItems result = new ApiInventoryReferencingItems();
    result.setReferencingItems(inventoryLinkManager.findReferencingItems(globalId, user));
    return result;
  }

  private ApiInventoryReferencingItems forItem(GlobalIdPrefix prefix, Long id, User user) {
    String globalId = new GlobalIdentifier(prefix, id).getIdString();
    ApiInventoryReferencingItems result = new ApiInventoryReferencingItems();
    result.setReferencingItems(inventoryLinkManager.findReferencingItems(globalId, user));
    return result;
  }
}
