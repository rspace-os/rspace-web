package com.researchspace.model.resourceaccess;

/** Stable keys used as the uniqueness boundary for resource-role grantees. */
public final class ResourceGranteeKeys {

  private ResourceGranteeKeys() {}

  public static String user(long userId) {
    return "user:" + userId;
  }

  public static String group(long groupId) {
    return "group:" + groupId;
  }

  public static String audience(ResourceAudience audience) {
    return "audience:" + audience.key();
  }
}
