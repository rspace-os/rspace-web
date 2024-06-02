package com.researchspace.model.events;

import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.InventoryRecord;
import lombok.Value;

@Value
public class InventoryMoveEvent implements MoveEvent<InventoryRecord, Container, Container> {

  private InventoryRecord movedItem;
  private Container sourceItem;
  private Container targetItem;
  private User movedBy;
}
