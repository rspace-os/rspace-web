package com.researchspace.model.sort;

/**
 * Sort keys for group listings. {@code OWNER} sorts by the owner's last name. {@code USAGE} selects
 * the file-usage aggregate query in the sysadmin group listing rather than a column.
 */
public enum GroupSort implements SortKey {
  DISPLAY_NAME("displayName"),
  MEMBER_COUNT("memberCount"),
  OWNER("owner"),
  USAGE("usage"),
  CREATION_DATE("creationDate"),
  GROUP_TYPE("groupType"),
  ID("id");

  private final String key;

  GroupSort(String key) {
    this.key = key;
  }

  @Override
  public String key() {
    return key;
  }

  public static GroupSort fromRequest(String raw) {
    return SortKey.fromRequest(GroupSort.class, raw, DISPLAY_NAME);
  }
}
