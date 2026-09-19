package com.researchspace.webapp.integrations.b2inst;

import org.apache.commons.lang3.StringUtils;

/**
 * Thrown when a call to the B2INST API fails. Mirrors the role of {@code
 * com.researchspace.datacite.model.DataCiteConnectionException} for the DataCite connector.
 *
 * <p>Carries the failure {@link #getReason() reason} separately from the message. The message is
 * developer-facing, goes to the logs, and may name internal detail such as a deployment property.
 * The reason is what a caller interpolates into a localized string for the user.
 *
 * <p>Deliberately no constructor that omits the reason, and {@link #getReason()} deliberately does
 * not fall back to the message. An earlier version had both, and two throw sites that supplied no
 * reason silently leaked an internal property name into user-facing text, with no compile error and
 * no failing test. A new throw site must now state its reason or fail to compile.
 *
 * <p>A reason is either B2INST's own words, parsed from its error body, or one of RSpace's own
 * sentences resolved from the message catalogue. Never the underlying exception's message, which
 * carries the request URL and host.
 *
 * <p>The reason is length-bounded here rather than by whoever displays it. B2INST's half of it is
 * derived from a response body that nothing upstream bounds, and it reaches a user through five
 * separate call sites, at least one of them a toast that waits to be dismissed. Bounding it at
 * construction is the same argument the connector makes for redacting the bearer token at a single
 * exit: a display site added later cannot forget to do it.
 */
public class B2instConnectionException extends RuntimeException {

  /** Matches the width the connector already abbreviates the same text to on its log path. */
  private static final int MAX_REASON_LENGTH = 500;

  private final String reason;

  /**
   * For a provider failure with no underlying exception.
   *
   * @param message developer-facing, logged, may carry internal detail
   * @param reason user-safe explanation, interpolated into localized text
   */
  public B2instConnectionException(String message, String reason) {
    this(message, reason, null);
  }

  /**
   * For a provider failure caused by another exception.
   *
   * @param message developer-facing, logged, may carry internal detail
   * @param reason user-safe explanation, interpolated into localized text
   */
  public B2instConnectionException(String message, String reason, Throwable cause) {
    super(message, cause);
    // every constructor delegates here, so this is the one place the bound has to hold
    this.reason = StringUtils.abbreviate(reason, MAX_REASON_LENGTH);
  }

  /**
   * Re-wraps a failure in a message that is <em>already</em> user-facing and localized, as the
   * service layer does around a connector failure. Carries no reason of its own, because its
   * message is the finished text. Do not use this for a provider failure: use a reason-carrying
   * constructor so the caller can compose the localized string itself.
   */
  public static B2instConnectionException wrapping(String localizedMessage, Throwable cause) {
    return new B2instConnectionException(localizedMessage, null, cause);
  }

  /**
   * The user-safe explanation, for interpolating into a localized message. {@code null} when this
   * exception was built by {@link #wrapping}, whose message is already the finished text.
   */
  public String getReason() {
    return reason;
  }
}
