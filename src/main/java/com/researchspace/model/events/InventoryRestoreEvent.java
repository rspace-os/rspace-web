package com.researchspace.model.events;

import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import lombok.Value;

@Value
public class InventoryRestoreEvent implements RestoreEvent<InventoryRecord> {

  private InventoryRecord restored;
  private User subject;
}
