package com.researchspace.service;

/** Failure of a collection-wide mutation guard. */
public class CollectionMutationException extends RuntimeException {

  private static final long serialVersionUID = 8879758023940946676L;

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
