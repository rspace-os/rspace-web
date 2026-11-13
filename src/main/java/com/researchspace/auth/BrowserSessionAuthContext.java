package com.researchspace.auth;

import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import java.util.UUID;

/** Rotating, opaque binding between a browser session and its REST API v2 UI tokens. */
public final class BrowserSessionAuthContext {

  public static final String UI_TOKEN_AUDIENCE = "rspace-rest-api-v2-ui";
  private static final String SESSION_ATTRIBUTE = "rs.API_V2_AUTH_CONTEXT";

  private BrowserSessionAuthContext() {}

  /** Returns the current binding, creating one when the session has not minted a token before. */
  public static String currentOrCreate(HttpSession session) {
    synchronized (session) {
      return current(session).orElseGet(() -> rotate(session));
    }
  }

  /** Returns the current binding without creating one. */
  public static Optional<String> current(HttpSession session) {
    Object value = session.getAttribute(SESSION_ATTRIBUTE);
    return value instanceof String context && !context.isBlank()
        ? Optional.of(context)
        : Optional.empty();
  }

  /** Invalidates existing UI tokens and returns the new binding. */
  public static String rotate(HttpSession session) {
    String context = UUID.randomUUID().toString();
    session.setAttribute(SESSION_ATTRIBUTE, context);
    return context;
  }
}
