package com.researchspace.model.events;

import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import lombok.Value;

@Value
public class InventoryCreationEvent implements CreationEvent<InventoryRecord> {

  private InventoryRecord createdItem;
  private User createdBy;
}
