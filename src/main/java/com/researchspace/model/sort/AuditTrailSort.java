package com.researchspace.model.sort;

/** Sort keys for the audit trail listing. */
public enum AuditTrailSort implements SortKey {
  DATE("date"),
  ACTION("action"),
  USERNAME("username");

  private final String key;

  AuditTrailSort(String key) {
    this.key = key;
  }

  @Override
  public String key() {
    return key;
  }

  public static AuditTrailSort fromRequest(String raw) {
    return SortKey.fromRequest(AuditTrailSort.class, raw, DATE);
  }
}
