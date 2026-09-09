package com.researchspace.model.sort;

/**
 * Sort keys for user listings: directory, community user list, sysadmin users and usage. {@code
 * FILE_USAGE} and {@code RECORD_COUNT} select an aggregate usage query rather than a column.
 */
public enum UserSort implements SortKey {
  LAST_NAME("lastName"),
  FIRST_NAME("firstName"),
  USERNAME("username"),
  EMAIL("email"),
  AFFILIATION("affiliation"),
  CREATION_DATE("creationDate"),
  LAST_LOGIN("lastLogin"),
  FILE_USAGE("fileUsage"),
  RECORD_COUNT("recordCount");

  private final String key;

  UserSort(String key) {
    this.key = key;
  }

  @Override
  public String key() {
    return key;
  }

  public static UserSort fromRequest(String raw) {
    return SortKey.fromRequest(UserSort.class, raw, LAST_NAME);
  }
}
