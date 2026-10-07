package com.researchspace.service.inventory;

import lombok.Getter;

/**
 * The PID being imported is already linked from an instrument in this deployment (RSDEV-1326,
 * decision 7). Mapped to HTTP 409 by {@code ApiControllerAdvice}.
 *
 * <p>Carries a message bundle key rather than a ready-made message, so each caller can render it in
 * the form its own clients expect. Which key is thrown is still the service's decision, because it
 * depends on a permission check: the naming key, with the instrument's globalId as its argument,
 * only when the caller may read that instrument; otherwise the no-access key, which carries no
 * arguments at all, so the globalId cannot leak through a renderer that reads them (RSDEV-1505; ADR
 * 0009 decision 4, amended).
 */
@Getter
public class PidinstAlreadyLinkedException extends RuntimeException {

  private final String messageKey;
  private final Object[] args;

  public PidinstAlreadyLinkedException(String messageKey, Object... args) {
    super(messageKey);
    this.messageKey = messageKey;
    this.args = args;
  }
}
