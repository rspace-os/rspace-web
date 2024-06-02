package com.researchspace.model.events;

import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import lombok.Value;

@Value
public class InventoryEditingEvent implements EditingEvent<InventoryRecord> {

  private InventoryRecord editedItem;
  private User editedBy;
}
