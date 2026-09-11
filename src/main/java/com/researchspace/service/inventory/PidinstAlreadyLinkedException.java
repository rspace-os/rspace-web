package com.researchspace.service.inventory;

/**
 * The PID being imported is already linked from an instrument in this deployment (RSDEV-1326,
 * decision 7). Mapped to HTTP 409 by {@code ApiControllerAdvice}; the localized message names the
 * instrument, whether or not the caller can read it (accepted disclosure of a globalId).
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
