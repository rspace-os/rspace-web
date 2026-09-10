package com.researchspace.model.sort;

/** Sort keys for the shared and published records listings. */
public enum SharedRecordSort implements SortKey {
  NAME("name"),
  SHAREE("sharee"),
  CREATION_DATE("creationDate"),
  CREATION_DATE_MILLIS("creationDateMillis"),
  MODIFICATION_DATE_MILLIS("modificationDateMillis");

  private final String key;

  SharedRecordSort(String key) {
    this.key = key;
  }

  @Override
  public String key() {
    return key;
  }

  public static SharedRecordSort fromRequest(String raw) {
    return SortKey.fromRequest(SharedRecordSort.class, raw, NAME);
  }
}
