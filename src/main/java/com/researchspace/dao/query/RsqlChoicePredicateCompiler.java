package com.researchspace.dao.query;

import static com.researchspace.dao.query.RsqlSqlFragments.jsonQuoted;

import com.researchspace.model.collection.FilterExpression;
import java.util.ArrayList;
import java.util.List;

/** Membership comparisons for choices persisted as a JSON array of strings. */
final class RsqlChoicePredicateCompiler implements RsqlFieldPredicateCompiler {
  @Override
  public String compile(FilterExpression.Comparison comparison, String valuePath, Context state) {
    List<String> disjuncts = new ArrayList<>();
    String safeJson =
        "CASE WHEN FUNCTION('JSON_VALID', "
            + valuePath
            + ") = true THEN "
            + valuePath
            + " ELSE '[]' END";
    for (Object value : comparison.values()) {
      String candidate = jsonQuoted(String.valueOf(value));
      disjuncts.add(
          "FUNCTION('JSON_CONTAINS', " + safeJson + ", :" + state.add(candidate) + ") = true");
    }
    return "(" + String.join(" OR ", disjuncts) + ")";
  }
}
