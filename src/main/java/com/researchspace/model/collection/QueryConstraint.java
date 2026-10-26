package com.researchspace.model.collection;

import java.util.List;

/** Trusted server-side query restriction, distinct from caller-parsed filter syntax. */
public sealed interface QueryConstraint
    permits FilterExpression,
        QueryConstraint.And,
        QueryConstraint.Or,
        ResourceRoleMembershipConstraint {

  /** Conjunction of heterogeneous trusted constraints. */
  record And(List<QueryConstraint> children) implements QueryConstraint {

    public And {
      children = List.copyOf(children);
      if (children.isEmpty()) {
        throw new IllegalArgumentException("Query constraint conjunction must have children");
      }
    }
  }

  /** Disjunction of heterogeneous trusted constraints. */
  record Or(List<QueryConstraint> children) implements QueryConstraint {

    public Or {
      children = List.copyOf(children);
      if (children.isEmpty()) {
        throw new IllegalArgumentException("Query constraint disjunction must have children");
      }
    }
  }
}
