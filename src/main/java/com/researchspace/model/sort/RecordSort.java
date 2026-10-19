package com.researchspace.model.sort;

/**
 * Sort keys for record listings: workspace folder contents and the gallery. {@code TEMPLATE} orders
 * documents by the name of the form they were created from.
 */
public enum RecordSort implements SortKey {
  NAME("name"),
  CREATION_DATE("creationDate"),
  CREATION_DATE_MILLIS("creationDateMillis"),
  MODIFICATION_DATE("modificationDate"),
  MODIFICATION_DATE_MILLIS("modificationDateMillis"),
  TEMPLATE("template");

  private final String key;

  RecordSort(String key) {
    this.key = key;
  }

  @Override
  public String key() {
    return key;
  }

  public static RecordSort fromRequest(String raw) {
    return SortKey.fromRequest(RecordSort.class, raw, MODIFICATION_DATE_MILLIS);
  }
}
