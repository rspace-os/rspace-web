package com.researchspace.model.collection;

/** Safe request-query failure that can cross the controller, manager, and persistence layers. */
public class CollectionQueryException extends RuntimeException {

  public enum Reason {
    SYNTAX,
    FIELD,
    OPERATOR,
    VALUE,
    COMPLEXITY
  }

  private final Reason reason;

  public CollectionQueryException(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public Reason getReason() {
    return reason;
  }
}
