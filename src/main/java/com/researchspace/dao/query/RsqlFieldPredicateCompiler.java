package com.researchspace.dao.query;

import com.researchspace.model.collection.FilterExpression;

/** Compiles a value comparison; storage correlation and presence checks belong to the caller. */
@FunctionalInterface
public interface RsqlFieldPredicateCompiler {
  /** Operation-local parameter binding and complexity accounting for trusted SQL adapters. */
  interface Context {
    /** Binds a value and returns its generated parameter name, without a leading colon. */
    String add(Object value);

    /** Charges one generated LIKE predicate to the caller's complexity budget. */
    void recordLikePredicate();
  }

  String compile(FilterExpression.Comparison comparison, String valuePath, Context state);
}
