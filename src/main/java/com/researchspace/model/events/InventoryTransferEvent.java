package com.researchspace.model.events;

import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import lombok.Value;

@Value
public class InventoryTransferEvent implements TransferEvent<InventoryRecord> {

  private InventoryRecord transferredItem;

  private User transferringUser;

  private User originalOwner;

  private User newOwner;
}
