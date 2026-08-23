package com.researchspace.model.collection;

import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of an access check: allow, deny, or allow subject to a query constraint.
 *
 * <p>The third case is the important one, and is modelled on PayloadCMS, whose access functions
 * return {@code boolean | Where}. Returning a {@link FilterExpression} turns access control into a
 * predicate that is folded into the query and pushed down to SQL, rather than a gate that filters
 * in memory afterwards. That keeps pagination totals correct and, because bulk operations take the
 * same {@link ResourceRequest}, makes a read constraint bound bulk writes automatically.
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
  record AllowedWhere(FilterExpression constraint) implements AccessResult {
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

  static AccessResult allowedWhere(FilterExpression constraint) {
    return new AllowedWhere(constraint);
  }

  default boolean isDenied() {
    return this instanceof Denied;
  }

  /** The constraint to AND into the caller's filter, if this result carries one. */
  default Optional<FilterExpression> constraintOrEmpty() {
    return this instanceof AllowedWhere allowedWhere
        ? Optional.of(allowedWhere.constraint())
        : Optional.empty();
  }
}
