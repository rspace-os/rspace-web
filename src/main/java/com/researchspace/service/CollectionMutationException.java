package com.researchspace.service;

import lombok.Getter;

/** Failure of a collection-wide mutation guard. */
public class CollectionMutationException extends RuntimeException {

  public enum Reason {
    FILTER_REQUIRED,
    BULK_LIMIT
  }

  @Getter private final Reason reason;

  public CollectionMutationException(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }
}
