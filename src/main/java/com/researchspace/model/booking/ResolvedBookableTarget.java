package com.researchspace.model.booking;

import com.researchspace.model.inventory.InventoryRecord;
import java.util.Objects;

/** A validated reference and the Inventory entity selected by REST relationship resolution. */
public record ResolvedBookableTarget(BookableTargetReference reference, InventoryRecord entity) {

  public ResolvedBookableTarget {
    Objects.requireNonNull(reference, "Target reference");
    Objects.requireNonNull(entity, "Target entity");
  }
}
