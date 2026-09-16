package com.researchspace.dao.query;

import static com.researchspace.dao.query.LikeEscaper.escape;
import static com.researchspace.dao.query.RsqlSqlFragments.jsonQuoted;

import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.FilterExpression;
import java.util.ArrayList;
import java.util.List;

/** Membership comparisons for choices persisted as a JSON array of strings. */
final class RsqlChoicePredicateCompiler implements RsqlFieldPredicateCompiler {
  @Override
  public String compile(
      FilterExpression.Comparison comparison, String valuePath, RsqlCompilationState state) {
    List<String> disjuncts = new ArrayList<>();
    for (Object value : comparison.values()) {
      state.recordLikePredicate();
      String needle = "%" + escape(jsonQuoted(String.valueOf(value))) + "%";
      disjuncts.add(valuePath + " LIKE :" + state.add(needle) + " ESCAPE '!'");
    }
    if (disjuncts.isEmpty()) {
      throw new CollectionQueryException(CollectionQueryException.Reason.VALUE);
    }
    return "(" + String.join(" OR ", disjuncts) + ")";
  }
}
