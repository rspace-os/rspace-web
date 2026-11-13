package com.researchspace.model.collection;

import cz.jirutka.rsql.parser.ast.Arity;
import cz.jirutka.rsql.parser.ast.ComparisonOperator;
import cz.jirutka.rsql.parser.ast.RSQLOperators;

public enum Operator {
  EQUAL(RSQLOperators.EQUAL),
  NOT_EQUAL(RSQLOperators.NOT_EQUAL),
  GREATER_THAN(RSQLOperators.GREATER_THAN),
  GREATER_THAN_OR_EQUAL(RSQLOperators.GREATER_THAN_OR_EQUAL),
  LESS_THAN(RSQLOperators.LESS_THAN),
  LESS_THAN_OR_EQUAL(RSQLOperators.LESS_THAN_OR_EQUAL),
  IN(RSQLOperators.IN),
  NOT_IN(RSQLOperators.NOT_IN),
  CONTAINS(new ComparisonOperator("=contains=", Arity.of(1, 1))),
  LIKE(new ComparisonOperator("=like=", Arity.of(1, 1))),
  EXISTS(new ComparisonOperator("=exists=", Arity.of(1, 1)));

  private final ComparisonOperator rsql;

  Operator(ComparisonOperator rsql) {
    this.rsql = rsql;
  }

  /** Returns the RSQL comparison operator the filter parser accepts for this operator. */
  public ComparisonOperator rsql() {
    return rsql;
  }

  /** Returns the canonical RSQL token accepted by the filter parser. */
  public String token() {
    return rsql.getSymbol();
  }
}
