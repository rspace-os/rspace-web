package com.researchspace.model.sort;

/** Sort keys for the form listing. {@code OWNER} sorts by the owner's username. */
public enum FormSort implements SortKey {
  NAME("name"),
  OWNER("owner"),
  PUBLISHING_STATE("publishingState"),
  MODIFICATION_DATE("modificationDate"),
  CREATION_DATE("creationDate"),
  CREATION_DATE_MILLIS("creationDateMillis"),
  MODIFICATION_DATE_MILLIS("modificationDateMillis"),
  ID("id");

  private final String key;

  FormSort(String key) {
    this.key = key;
  }

  @Override
  public String key() {
    return key;
  }

  public static FormSort fromRequest(String raw) {
    return SortKey.fromRequest(FormSort.class, raw, NAME);
  }
}
