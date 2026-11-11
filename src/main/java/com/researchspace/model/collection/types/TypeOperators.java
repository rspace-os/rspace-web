package com.researchspace.model.collection.types;

import com.researchspace.model.collection.Operator;
import java.util.Set;

final class TypeOperators {
  static final Set<Operator> ORDERED =
      Set.of(
          Operator.EQUAL,
          Operator.NOT_EQUAL,
          Operator.GREATER_THAN,
          Operator.GREATER_THAN_OR_EQUAL,
          Operator.LESS_THAN,
          Operator.LESS_THAN_OR_EQUAL,
          Operator.IN,
          Operator.NOT_IN);
  static final Set<Operator> TEXT =
      Set.of(
          Operator.EQUAL,
          Operator.NOT_EQUAL,
          Operator.IN,
          Operator.NOT_IN,
          Operator.CONTAINS,
          Operator.LIKE,
          Operator.EXISTS);
  static final Set<Operator> BOOLEAN = Set.of(Operator.EQUAL, Operator.NOT_EQUAL, Operator.EXISTS);

  private TypeOperators() {}
}
