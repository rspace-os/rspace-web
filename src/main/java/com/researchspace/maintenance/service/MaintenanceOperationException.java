package com.researchspace.maintenance.service;

import lombok.Getter;

/** Domain failure while applying a maintenance collection mutation. */
public class MaintenanceOperationException extends RuntimeException {

  public enum Reason {
    INVALID_WINDOW
  }

  @Getter private final Reason reason;

  public MaintenanceOperationException(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }
}
