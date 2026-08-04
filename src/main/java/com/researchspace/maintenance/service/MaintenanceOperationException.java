package com.researchspace.maintenance.service;

/** Domain failure while applying a maintenance collection mutation. */
public class MaintenanceOperationException extends RuntimeException {

  public enum Reason {
    INVALID_WINDOW
  }

  private final Reason reason;

  public MaintenanceOperationException(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public Reason getReason() {
    return reason;
  }
}
