package com.researchspace.model.events;

import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import lombok.Value;

@Value
public class InventoryAccessEvent implements AccessEvent<InventoryRecord> {

  private InventoryRecord accessedItem;
  private User accessedBy;
}
