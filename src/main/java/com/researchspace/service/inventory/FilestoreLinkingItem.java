package com.researchspace.service.inventory;

/**
 * An Inventory item whose Link field targets a Filestore item, as far as the asking user may see
 * it. A placeholder (the user cannot read the item in full) carries only {@code type} and {@code
 * ownerFullName}.
 */
public record FilestoreLinkingItem(
    String globalId, String name, String type, String ownerFullName, String relationType) {

  public boolean isReadable() {
    return globalId != null;
  }
}
