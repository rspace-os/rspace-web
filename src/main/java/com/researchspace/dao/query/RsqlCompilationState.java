package com.researchspace.dao.query;

import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.RuntimeFieldSelection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parameters and subqueries belong to a scope; generated names span its whole compilation. */
final class RsqlCompilationState {

  /** Owned by one compilation, including its nested scopes. Never shared by independent calls. */
  static final class Names {
    private final String prefix;
    private int parameters;
    private int aliases;

    Names(String prefix) {
      this.prefix = prefix;
    }

    String parameter() {
      return prefix + parameters++;
    }

    String alias() {
      return prefix + "Target" + aliases++;
    }
  }

  private final Names names;
  private final boolean enforceComplexityLimit;
  private int likePredicates;
  private final Map<String, Object> parameters = new LinkedHashMap<>();
  private final Map<String, RsqlCollectionQuery.Subquery> subqueries = new LinkedHashMap<>();

  final RelationshipReadAccess targets;
  final RuntimeFieldSelection runtime;

  RsqlCompilationState(
      Names names,
      RelationshipReadAccess targets,
      RuntimeFieldSelection runtime,
      boolean enforceComplexityLimit) {
    this.names = names;
    this.targets = targets;
    this.runtime = runtime;
    this.enforceComplexityLimit = enforceComplexityLimit;
  }

  RsqlCompilationState nested(boolean enforceLimit) {
    return new RsqlCompilationState(
        names, RelationshipReadAccess.none(), RuntimeFieldSelection.empty(), enforceLimit);
  }

  boolean enforcesComplexityLimit() {
    return enforceComplexityLimit;
  }

  RsqlCollectionQuery.Predicate predicate(String expression) {
    return new RsqlCollectionQuery.Predicate(expression, parameters, subqueries, likePredicates);
  }

  String nextTargetAlias() {
    return names.alias();
  }

  String merge(RsqlCollectionQuery.Predicate predicate) {
    RsqlCollectionQuery.mergeMappings(parameters, predicate.parameters(), "parameter");
    RsqlCollectionQuery.mergeMappings(subqueries, predicate.subqueries(), "subquery");
    recordLikePredicates(predicate.callerLikePredicateCount());
    return predicate.expression();
  }

  String addSubquery(RsqlCollectionQuery.Subquery subquery) {
    String name = subquery.alias() + "Sub";
    RsqlCollectionQuery.mergeMappings(subqueries, Map.of(name, subquery), "subquery");
    return name;
  }

  /** Keeps the nested scope's subqueries inside their correlated parent. */
  String addSubquery(
      Class<?> entityType, String alias, String expression, RsqlCompilationState scope) {
    RsqlCollectionQuery.mergeMappings(parameters, scope.parameters, "parameter");
    recordLikePredicates(scope.likePredicates);
    return addSubquery(
        new RsqlCollectionQuery.Subquery(entityType, alias, expression, scope.subqueries));
  }

  String add(Object value) {
    String name = names.parameter();
    RsqlCollectionQuery.mergeMappings(parameters, Map.of(name, value), "parameter");
    return name;
  }

  void recordLikePredicate() {
    recordLikePredicates(1);
  }

  private void recordLikePredicates(int count) {
    if (!enforceComplexityLimit) {
      return;
    }
    likePredicates += count;
    if (likePredicates > RsqlCollectionQuery.MAX_LIKE_PREDICATES) {
      throw new CollectionQueryException(CollectionQueryException.Reason.COMPLEXITY);
    }
  }
}
