package com.researchspace.maintenance.service;

/** Domain failure while applying a maintenance collection mutation. */
public class MaintenanceOperationException extends RuntimeException {

  public MaintenanceOperationException() {
    super("INVALID_WINDOW");
  }
}
