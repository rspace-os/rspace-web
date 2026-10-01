package com.researchspace.dao.query;

import static com.researchspace.dao.query.LikeEscaper.escape;
import static com.researchspace.dao.query.RsqlSqlFragments.like;
import static com.researchspace.dao.query.RsqlSqlFragments.symbol;
import static com.researchspace.dao.query.RsqlSqlFragments.wildcardPattern;
import static com.researchspace.dao.query.RsqlSqlFragments.wordsLike;

import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;

/** Comparisons against native scalar properties or canonical text values. */
final class RsqlScalarPredicateCompiler implements RsqlFieldPredicateCompiler {
  @Override
  public String compile(
      FilterExpression.Comparison comparison, String path, RsqlCompilationState state) {
    Operator operator = comparison.operator();
    if (operator == Operator.IN || operator == Operator.NOT_IN) {
      String parameter = state.add(comparison.values());
      return path + (operator == Operator.IN ? " IN :" : " NOT IN :") + parameter;
    }
    Object value = comparison.values().get(0);
    if (operator == Operator.CONTAINS) {
      state.recordLikePredicate();
      return like(path, state.add("%" + escape(String.valueOf(value)) + "%"), false);
    }
    if (operator == Operator.LIKE) {
      return wordsLike(path, String.valueOf(value), state);
    }
    if (comparison.wildcard()) {
      state.recordLikePredicate();
      return like(
          path, state.add(wildcardPattern(String.valueOf(value))), operator == Operator.NOT_EQUAL);
    }
    return path + " " + symbol(operator) + " :" + state.add(value);
  }
}
