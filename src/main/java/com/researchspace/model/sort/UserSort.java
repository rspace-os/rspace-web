package com.researchspace.model.sort;

import java.util.Arrays;

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

  /** Resolves keys that map directly to a User property rather than an aggregate query. */
  public static UserSort fromColumnRequest(String raw) {
    UserSort sort = fromRequest(raw);
    if (sort == FILE_USAGE || sort == RECORD_COUNT) {
      throw new UnknownSortKeyException(
          raw,
          Arrays.stream(values())
              .filter(candidate -> candidate != FILE_USAGE && candidate != RECORD_COUNT)
              .map(SortKey::key)
              .toList());
    }
    return sort;
  }
}
