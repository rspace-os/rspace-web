package com.researchspace.model.collection;

import com.researchspace.model.collection.CollectionDescription.Operator;
import java.util.List;
import java.util.Objects;

/** Typed, persistence-neutral filter tree produced from a supported query dialect. */
public sealed interface FilterExpression
    permits FilterExpression.And, FilterExpression.Comparison, FilterExpression.Or {

  record Comparison(String field, Operator operator, List<Object> values, boolean wildcard)
      implements FilterExpression {

    public Comparison {
      if (field == null || field.isBlank()) {
        throw new IllegalArgumentException("Filter field must not be blank");
      }
      Objects.requireNonNull(operator, "Filter operator");
      values = List.copyOf(values);
      if (values.isEmpty()
          || (operator != Operator.IN && operator != Operator.NOT_IN && values.size() != 1)) {
        throw new IllegalArgumentException("Filter operator has an invalid number of values");
      }
      if (wildcard && operator != Operator.EQUAL && operator != Operator.NOT_EQUAL) {
        throw new IllegalArgumentException("Wildcards require equality comparison");
      }
    }
  }

  record And(List<FilterExpression> children) implements FilterExpression {

    public And {
      children = nonEmpty(children);
    }
  }

  record Or(List<FilterExpression> children) implements FilterExpression {

    public Or {
      children = nonEmpty(children);
    }
  }

  private static List<FilterExpression> nonEmpty(List<FilterExpression> children) {
    List<FilterExpression> copy = List.copyOf(children);
    if (copy.isEmpty()) {
      throw new IllegalArgumentException("Logical filter must have children");
    }
    return copy;
  }
}
