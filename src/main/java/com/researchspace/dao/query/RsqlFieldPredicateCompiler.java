package com.researchspace.dao.query;

import com.researchspace.model.collection.FilterExpression;

/** Compiles a value comparison; storage correlation and presence checks belong to the caller. */
@FunctionalInterface
interface RsqlFieldPredicateCompiler {
  String compile(
      FilterExpression.Comparison comparison, String valuePath, RsqlCompilationState state);
}
