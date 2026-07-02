package com.researchspace.webapp.integrations.helper;

import org.springframework.ui.Model;

/**
 * Helpers for the shared OAuth/credential result page (connect/connected.jsp), which broadcasts the
 * connection outcome back to the React Apps page and closes the popup window.
 */
public final class ConnectionResultPage {

  /** View name returned by integration controllers to render the shared result page. */
  public static final String VIEW = "connect/connected";

  private ConnectionResultPage() {}

  /** Sets the attributes the shared page needs to broadcast a successful connection. */
  public static void addConnectionAttributes(
      Model model, String appName, String channel, String type) {
    model.addAttribute("appName", appName);
    model.addAttribute("connectionChannel", channel);
    model.addAttribute("connectionType", type);
  }

  /**
   * Sets the connection attributes plus a user-facing error message, so the shared page broadcasts
   * a failure.
   */
  public static void addError(
      Model model, String appName, String channel, String type, OauthAuthorizationError error) {
    addConnectionAttributes(model, appName, channel, type);
    model.addAttribute("connectionError", buildErrorMessage(error));
  }

  /** Builds a user-facing error string from an {@link OauthAuthorizationError}. */
  public static String buildErrorMessage(OauthAuthorizationError error) {
    String message = error.getErrorMsg();
    if (error.getErrorDetails() != null && !error.getErrorDetails().isEmpty()) {
      message += ": " + error.getErrorDetails();
    }
    return message;
  }
}
