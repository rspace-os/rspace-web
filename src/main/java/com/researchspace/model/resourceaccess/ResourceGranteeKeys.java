package com.researchspace.model.resourceaccess;

import org.apache.commons.lang3.math.NumberUtils;

/** Stable keys used as the uniqueness boundary for resource-role grantees. */
public final class ResourceGranteeKeys {

  private static final String USER_PREFIX = "user:";
  private static final String GROUP_PREFIX = "group:";

  private ResourceGranteeKeys() {}

  public static String user(long userId) {
    return USER_PREFIX + userId;
  }

  public static String group(long groupId) {
    return GROUP_PREFIX + groupId;
  }

  public static String audience(ResourceAudience audience) {
    return "audience:" + audience.key();
  }

  public static Long parseUserId(String key) {
    return parseId(key, USER_PREFIX);
  }

  public static Long parseGroupId(String key) {
    return parseId(key, GROUP_PREFIX);
  }

  /** Returns the part after the first colon for API presentation, or {@code null} if absent. */
  public static String parseIdentifier(String key) {
    if (key == null) {
      return null;
    }
    int separator = key.indexOf(':');
    return separator < 0 ? null : key.substring(separator + 1);
  }

  private static Long parseId(String key, String prefix) {
    if (key == null || !key.startsWith(prefix)) {
      return null;
    }
    long id = NumberUtils.toLong(key.substring(prefix.length()));
    return id == 0 ? null : id;
  }
}
