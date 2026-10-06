package com.researchspace.session;

import java.io.Serializable;
import java.time.Duration;

/** Session-bound GitHub authorization used while selecting repositories. */
public final class GitHubPendingToken {
  private static final String ATTRIBUTE = "rs.github.pendingToken";
  private static final long LIFETIME_MILLIS = Duration.ofMinutes(15).toMillis();

  private GitHubPendingToken() {}

  public static void store(String username, String token) {
    SessionAttributeUtils.setSessionAttribute(
        ATTRIBUTE, new Token(username, token, System.currentTimeMillis() + LIFETIME_MILLIS));
  }

  public static String get(String username) {
    return get(username, System.currentTimeMillis());
  }

  static String get(String username, long now) {
    Object value = SessionAttributeUtils.getSessionAttribute(ATTRIBUTE);
    if (value instanceof Token pending
        && pending.username.equals(username)
        && pending.expiresAt > now) {
      return pending.token;
    }
    clear();
    return null;
  }

  public static void clear() {
    SessionAttributeUtils.removeSessionAttribute(ATTRIBUTE);
  }

  private static final class Token implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String username;
    private final String token;
    private final long expiresAt;

    private Token(String username, String token, long expiresAt) {
      this.username = username;
      this.token = token;
      this.expiresAt = expiresAt;
    }
  }
}
