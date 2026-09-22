package com.researchspace.service.inventory;

/**
 * The PID being imported is already linked from an instrument in this deployment (RSDEV-1326,
 * decision 7). Mapped to HTTP 409 by {@code ApiControllerAdvice}. The localized message names the
 * instrument only when the caller may read it; otherwise it says an instrument they cannot access
 * holds the PID and {@link #getLinkedInstrumentGlobalId()} is null (RSDEV-1505; ADR 0009 decision
 * 4, amended).
 */
public class PidinstAlreadyLinkedException extends RuntimeException {

  private final String linkedInstrumentGlobalId;

  public PidinstAlreadyLinkedException(String localizedMessage, String linkedInstrumentGlobalId) {
    super(localizedMessage);
    this.linkedInstrumentGlobalId = linkedInstrumentGlobalId;
  }

  public String getLinkedInstrumentGlobalId() {
    return linkedInstrumentGlobalId;
  }
}
