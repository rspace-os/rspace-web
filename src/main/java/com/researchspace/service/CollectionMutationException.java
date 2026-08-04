package com.researchspace.service;

/** Failure of a collection-wide mutation guard. */
public class CollectionMutationException extends RuntimeException {

  public enum Reason {
    FILTER_REQUIRED,
    BULK_LIMIT
  }

  private final Reason reason;

  public CollectionMutationException(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public Reason getReason() {
    return reason;
  }
}
