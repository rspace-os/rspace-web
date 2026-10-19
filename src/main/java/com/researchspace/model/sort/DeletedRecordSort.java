package com.researchspace.model.sort;

/** Sort keys for the deleted-records listing. */
public enum DeletedRecordSort implements SortKey {
  DELETED_DATE("deletedDate"),
  NAME("name"),
  CREATION_DATE("creationDate"),
  MODIFICATION_DATE("modificationDate");

  private final String key;

  DeletedRecordSort(String key) {
    this.key = key;
  }

  @Override
  public String key() {
    return key;
  }

  public static DeletedRecordSort fromRequest(String raw) {
    return SortKey.fromRequest(DeletedRecordSort.class, raw, DELETED_DATE);
  }
}
