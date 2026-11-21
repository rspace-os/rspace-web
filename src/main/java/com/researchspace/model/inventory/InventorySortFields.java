package com.researchspace.model.inventory;

import com.researchspace.model.inventory.InventoryRecord.InventoryRecordType;
import java.util.Comparator;
import java.util.Date;

/** Fields used to sort inventory entities and scalar projections of container occupants. */
public interface InventorySortFields {

  Comparator<InventorySortFields> BY_NAME =
      Comparator.comparing(record -> record.getName().toLowerCase());
  Comparator<InventorySortFields> BY_TYPE =
      Comparator.comparing(record -> record.getType().toString());
  Comparator<InventorySortFields> BY_ID = Comparator.comparing(InventorySortFields::getId);
  Comparator<InventorySortFields> BY_CREATED =
      Comparator.comparing(InventorySortFields::getCreationDate);
  Comparator<InventorySortFields> BY_MODIFIED =
      Comparator.comparing(InventorySortFields::getModificationDate);

  Long getId();

  String getName();

  InventoryRecordType getType();

  Date getCreationDate();

  Date getModificationDate();
}
