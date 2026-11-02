package com.researchspace.model.resourceaccess;

/** Dynamic audiences supported by generic resource-role assignments. */
public enum ResourceAudience {
  ALL_USERS("all-users", "resourceAccess.audiences.allUsers");

  private final String key;
  private final String messageKey;

  ResourceAudience(String key, String messageKey) {
    this.key = key;
    this.messageKey = messageKey;
  }

  public String key() {
    return key;
  }

  public String messageKey() {
    return messageKey;
  }
}
