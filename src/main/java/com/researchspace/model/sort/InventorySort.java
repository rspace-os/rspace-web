package com.researchspace.model.sort;

/** Sort keys for inventory record listings. */
public enum InventorySort implements SortKey {
  NAME("name"),
  TYPE("type"),
  GLOBAL_ID("globalId"),
  CREATION_DATE("creationDate"),
  MODIFICATION_DATE("modificationDate");

  private final String key;

  InventorySort(String key) {
    this.key = key;
  }

  @Override
  public String key() {
    return key;
  }

  public static InventorySort fromRequest(String raw) {
    return SortKey.fromRequest(InventorySort.class, raw, NAME);
  }
}
