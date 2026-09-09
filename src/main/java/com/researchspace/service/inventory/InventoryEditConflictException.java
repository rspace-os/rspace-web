package com.researchspace.service.inventory;

import lombok.Getter;

/**
 * An Inventory write lost a race with a concurrent one: the request was well-formed and valid
 * against the state the client read, but that state has since changed. Maps to 409 with {@code
 * ApiErrorCodes.EDIT_CONFLICT} (ApiControllerAdvice), not 400: nothing about the request is
 * malformed, so the caller's remedy is to reload and retry rather than to correct a field.
 *
 * <p>Unchecked deliberately. The operations endpoint's transaction advice declares {@code
 * rollback-for BindException} only because BindException is checked; a RuntimeException rolls back
 * under Spring's default rules, so the sibling-set locks the live-state pass takes cannot commit
 * alongside a 409.
 *
 * <p>Carries a message KEY rather than a message, resolved by the advice through {@code
 * MessageSourceUtils}, matching {@code ApiRuntimeException}.
 */
@Getter
public class InventoryEditConflictException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String messageKey;
  private final Object[] args;

  public InventoryEditConflictException(String messageKey, Object... args) {
    super(messageKey);
    this.messageKey = messageKey;
    this.args = args;
  }
}
