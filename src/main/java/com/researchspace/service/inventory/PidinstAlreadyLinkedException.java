package com.researchspace.service.inventory;

/**
 * The PID being imported is already linked from an instrument in this deployment (RSDEV-1326,
 * decision 7). Mapped to HTTP 409 by {@code ApiControllerAdvice}, which builds its response from
 * the localized message alone. That message names the instrument only when the caller may read it;
 * otherwise it says only that an instrument they cannot access holds the PID (RSDEV-1505; ADR 0009
 * decision 4, amended).
 */
public class PidinstAlreadyLinkedException extends RuntimeException {

  public PidinstAlreadyLinkedException(String localizedMessage) {
    super(localizedMessage);
  }
}
