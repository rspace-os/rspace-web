package com.researchspace.dao.query;

import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.RuntimeFieldSelection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The mutable state built up while compiling one collection filter: the bound parameters, the
 * subqueries hoisted out of it, and the counters that keep generated names distinct and hold the
 * complexity limit.
 *
 * <p>Threaded through the compiler as an explicit argument rather than held on {@link
 * RsqlCollectionQuery}, so that a single query object can compile several predicates without them
 * bleeding into each other.
 */
final class RsqlCompilationState {

  private final String prefix;
  private final boolean enforceComplexityLimit;
  private int sequence;
  private int likePredicates;
  private int targetAliases;

  final RelationshipReadAccess targets;
  final RuntimeFieldSelection runtime;
  final Map<String, Object> parameters = new LinkedHashMap<>();
  final Map<String, RsqlCollectionQuery.Subquery> subqueries = new LinkedHashMap<>();

  RsqlCompilationState(
      String prefix,
      RelationshipReadAccess targets,
      RuntimeFieldSelection runtime,
      boolean enforceComplexityLimit) {
    this.prefix = prefix;
    this.targets = targets;
    this.runtime = runtime;
    this.enforceComplexityLimit = enforceComplexityLimit;
  }

  /** A distinct alias per subquery, so nested targets cannot shadow each other. */
  String nextTargetAlias() {
    return prefix + "Target" + targetAliases++;
  }

  /** Folds a separately compiled predicate in, keeping its already-distinct parameter names. */
  String merge(RsqlCollectionQuery.Predicate predicate) {
    parameters.putAll(predicate.parameters());
    subqueries.putAll(predicate.subqueries());
    return predicate.expression();
  }

  /** A placeholder distinct from the subquery's own entity alias, which Blaze also parses. */
  String addSubquery(RsqlCollectionQuery.Subquery subquery) {
    String name = subquery.alias() + "Sub";
    subqueries.put(name, subquery);
    return name;
  }

  String add(Object value) {
    String name = prefix + sequence++;
    parameters.put(name, value);
    return name;
  }

  void recordLikePredicate() {
    if (enforceComplexityLimit && ++likePredicates > RsqlCollectionQuery.MAX_LIKE_PREDICATES) {
      throw new CollectionQueryException(CollectionQueryException.Reason.COMPLEXITY);
    }
  }
}
