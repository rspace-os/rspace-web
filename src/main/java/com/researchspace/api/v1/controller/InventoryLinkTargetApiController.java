package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.InventoryLinkTargetApi;
import com.researchspace.api.v1.model.ApiInventoryLinkTargetSummary;
import com.researchspace.model.User;
import com.researchspace.service.inventory.InventoryLinkManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;

@ApiController
public class InventoryLinkTargetApiController implements InventoryLinkTargetApi {

  @Autowired private InventoryLinkManager inventoryLinkManager;

  @Override
  public ApiInventoryLinkTargetSummary getLinkTargetSummary(
      @PathVariable String globalId, @RequestAttribute(name = "user") User user) {
    return inventoryLinkManager.getTargetSummary(globalId, user);
  }
}
