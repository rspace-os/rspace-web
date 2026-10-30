package com.researchspace.model.collection;

import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of an access check: allow, deny, or allow subject to a query constraint.
 *
 * <p>A constraint can be compiled into a database predicate. Collection managers and DAOs must
 * apply read constraints to list and count queries. Relationship resolution uses the target read
 * constraint. Read constraints do not yet automatically restrict writes.
 */
public sealed interface AccessResult {

  /** Unconditional permission. */
  record Allowed() implements AccessResult {}

  /**
   * Refusal.
   *
   * @param reasonCode message key describing the refusal; never the name of a field or resource,
   *     which would tell the caller what exists
   */
  record Denied(String reasonCode) implements AccessResult {
    public Denied {
      if (reasonCode == null || reasonCode.isBlank()) {
        throw new IllegalArgumentException("Denial reason code must not be blank");
      }
    }
  }

  /** Permission limited to the rows matching {@code constraint}. */
  record AllowedWhere(QueryConstraint constraint) implements AccessResult {
    public AllowedWhere {
      Objects.requireNonNull(constraint, "Access constraint");
    }
  }

  static AccessResult allowed() {
    return new Allowed();
  }

  static AccessResult denied(String reasonCode) {
    return new Denied(reasonCode);
  }

  static AccessResult allowedWhere(QueryConstraint constraint) {
    return new AllowedWhere(constraint);
  }

  default boolean isDenied() {
    return this instanceof Denied;
  }

  /** The constraint to AND into the caller's filter, if this result carries one. */
  default Optional<QueryConstraint> constraintOrEmpty() {
    return this instanceof AllowedWhere allowedWhere
        ? Optional.of(allowedWhere.constraint())
        : Optional.empty();
  }
}
