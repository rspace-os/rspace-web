package com.researchspace.model.resourceaccess;

/** Dynamic audiences supported by generic resource-role assignments. */
public enum ResourceAudience {
  ALL_USERS("all-users", "All users");

  private final String key;
  private final String displayName;

  ResourceAudience(String key, String displayName) {
    this.key = key;
    this.displayName = displayName;
  }

  public String key() {
    return key;
  }

  public String displayName() {
    return displayName;
  }
}
