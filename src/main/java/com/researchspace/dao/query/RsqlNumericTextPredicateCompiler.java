package com.researchspace.dao.query;

import static com.researchspace.dao.query.RsqlSqlFragments.symbol;

import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Numeric comparisons for values persisted as text, using the bounded numeric conversion. */
final class RsqlNumericTextPredicateCompiler implements RsqlFieldPredicateCompiler {
  private static final String NUMERIC = NumericTextFunction.NAME.toUpperCase(Locale.ROOT);

  @Override
  public String compile(
      FilterExpression.Comparison comparison, String valuePath, RsqlCompilationState state) {
    String numeric = NUMERIC + "(" + valuePath + ")";
    Operator operator = comparison.operator();
    if (operator == Operator.IN || operator == Operator.NOT_IN) {
      List<String> equalities = new ArrayList<>();
      for (Object value : comparison.values()) {
        equalities.add(numeric + " = :" + state.add(value));
      }
      if (equalities.isEmpty()) {
        throw new CollectionQueryException(CollectionQueryException.Reason.VALUE);
      }
      String any = "(" + String.join(" OR ", equalities) + ")";
      return operator == Operator.IN ? any : "NOT " + any;
    }
    return numeric + " " + symbol(operator) + " :" + state.add(comparison.values().get(0));
  }
}
