package com.researchspace.service.inventory;

import lombok.Getter;

/**
 * Another request is already operating on this origin; the caller retries once it has finished. The
 * API boundary maps it to 409.
 */
@Getter
public class InventoryOperationInProgressException extends RuntimeException {

  private final String globalId;

  public InventoryOperationInProgressException(String globalId) {
    super("an operation on " + globalId + " is already in progress");
    this.globalId = globalId;
  }
}
