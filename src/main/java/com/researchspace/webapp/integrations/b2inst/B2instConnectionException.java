package com.researchspace.webapp.integrations.b2inst;

/**
 * Thrown when a call to the B2INST API fails. Mirrors the role of {@code
 * com.researchspace.datacite.model.DataCiteConnectionException} for the DataCite connector.
 *
 * <p>Carries the failure {@link #getReason() reason} separately from the message. The message is
 * the developer-facing sentence that names the operation and goes to the logs, and it may contain
 * internal detail such as a deployment property name. The reason is the part that is safe and
 * useful to show a user, and it is what callers interpolate into a localized string. Keeping them
 * apart is the point of this class: interpolating the message would put an English developer prefix
 * inside a translated sentence and duplicate what that sentence already says.
 *
 * <p>There is deliberately <strong>no</strong> convenience constructor that omits the reason, and
 * {@link #getReason()} deliberately does <strong>not</strong> fall back to the message. An earlier
 * version had both, and the result was that two throw sites which never supplied a reason silently
 * leaked their full developer message, including an internal property name, into a user-facing
 * string with no compile error and no failing test. A new provider-failure throw site must now
 * state its reason or fail to compile.
 *
 * <p>Where a reason comes from, and what that costs. Only one of the three shapes is B2INST's own
 * words: the description parsed out of a B2INST error body, which is the useful case and cannot be
 * translated. The other two are RSpace's own fixed English - the HTTP status when the body explains
 * nothing, and a "could not be reached" sentence for a transport failure. Both are deliberately
 * fixed strings rather than the underlying exception, because Spring's message for a transport
 * failure carries the request URL and host and its blank-message fallback was the exception class
 * name, and callers interpolate this value into localized text and into the audit trail.
 *
 * <p>Those two therefore ship untranslated inside an otherwise translated sentence. That is the
 * accepted trade-off rather than an oversight: localizing them would need a message source in the
 * connector, and the service package already imports this one, so injecting it back would close a
 * package cycle. If it becomes worth fixing, the better shape is for this exception to carry the
 * status and the failure kind as data and let each caller - all of which already hold a message
 * source - compose the sentence.
 */
public class B2instConnectionException extends RuntimeException {

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
    this.reason = reason;
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
