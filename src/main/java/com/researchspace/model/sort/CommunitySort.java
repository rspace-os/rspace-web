package com.researchspace.model.sort;

/** Sort keys for community listings. */
public enum CommunitySort implements SortKey {
  DISPLAY_NAME("displayName");

  private final String key;

  CommunitySort(String key) {
    this.key = key;
  }

  @Override
  public String key() {
    return key;
  }

  public static CommunitySort fromRequest(String raw) {
    return SortKey.fromRequest(CommunitySort.class, raw, DISPLAY_NAME);
  }
}
