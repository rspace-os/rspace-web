package com.researchspace.service.inventory;

import com.researchspace.model.inventory.InventoryRecord.InventoryRecordType;
import java.util.Date;

/**
 * A structure hit (see CONTEXT.md) as listed outside Inventory, read while the record is still
 * attached to the session, so callers need no transaction of their own.
 */
public record InventoryStructureHit(
    String globalId,
    String name,
    InventoryRecordType type,
    String ownerFullName,
    Date lastModified) {}
