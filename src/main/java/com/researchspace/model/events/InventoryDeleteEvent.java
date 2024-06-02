package com.researchspace.model.events;

import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import lombok.Value;

@Value
public class InventoryDeleteEvent implements DeleteEvent<InventoryRecord> {

  private InventoryRecord deletedItem;
  private User deletedBy;
}
