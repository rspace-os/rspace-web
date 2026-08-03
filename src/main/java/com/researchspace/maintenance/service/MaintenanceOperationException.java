package com.researchspace.maintenance.service;

/** Domain failure while applying a maintenance collection mutation. */
public class MaintenanceOperationException extends RuntimeException {

  private static final long serialVersionUID = -8397253690055157362L;

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
