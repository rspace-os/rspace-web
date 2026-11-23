package com.researchspace.service.dmp;

/**
 * The outcome of sending one value to a plan.
 *
 * @param error the reason it was not written, or null when written
 */
public record ValueOutcome(ReportedValueKind kind, String value, boolean written, String error) {

  /** Message key of the reason shown for an unexpected error; the exception itself is logged. */
  public static final String UNEXPECTED_ERROR_KEY =
      "email.notification.dmpUpdateCompleteNotification.unexpectedError";

  /** The value this deposit sends (its DOI link, else its access URL), marked as not written. */
  public static ValueOutcome failed(DepositReport deposit, String error) {
    if (deposit.identifier() != null) {
      return new ValueOutcome(
          ReportedValueKind.IDENTIFIER, deposit.identifier().toString(), false, error);
    }
    String accessUrl = deposit.accessUrl() == null ? null : deposit.accessUrl().toString();
    return new ValueOutcome(ReportedValueKind.ACCESS_URL, accessUrl, false, error);
  }
}
