package com.researchspace.dao.query;

import static com.researchspace.dao.query.RsqlSqlFragments.referencePair;
import static com.researchspace.dao.query.RsqlSqlFragments.symbol;

import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.FilterSelector;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.Relationship;
import com.researchspace.model.collection.RelationshipTarget;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.ResourceRegistry.RelationshipQueryPath;
import com.researchspace.model.collection.ResourceRegistry.TargetQueryField;
import com.researchspace.model.collection.SplitReferenceBinding;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Compiles relationship paths, references, and correlated target-access subqueries. */
final class RsqlRelationshipCompiler {

  private final CollectionDescription<?> description;
  private final String alias;

  RsqlRelationshipCompiler(CollectionDescription<?> description, String alias) {
    this.description = description;
    this.alias = alias;
  }

  String compileRelationshipField(
      FilterExpression.Comparison comparison, RsqlCompilationState state) {
    RelationshipQueryPath path =
        state
            .targets
            .findPath(description.resourceName(), comparison.field())
            .orElseThrow(() -> new CollectionQueryException(CollectionQueryException.Reason.FIELD));
    if (!path.filterSelector().operators().contains(comparison.operator())) {
      throw new CollectionQueryException(CollectionQueryException.Reason.OPERATOR);
    }
    List<String> disjuncts = new ArrayList<>();
    for (TargetQueryField target : path.targets()) {
      String disjunct = targetExists(comparison, path, target, state);
      if (disjunct != null) {
        disjuncts.add(disjunct);
      }
    }
    if (disjuncts.isEmpty()) {
      throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
    }
    return "(" + String.join(" OR ", disjuncts) + ")";
  }

  private String targetExists(
      FilterExpression.Comparison comparison,
      RelationshipQueryPath path,
      TargetQueryField targetField,
      RsqlCompilationState state) {
    RelationshipTarget<?> target = targetField.target();
    CollectionDescription<?> targetDescription = state.targets.description(target.resourceName());
    if (targetDescription == null) {
      return null;
    }
    AccessResult access = state.targets.result(target.resourceName());
    if (access.isDenied()) {
      return "1 = 0";
    }
    return readableTargetExists(
        path.relationship(),
        target,
        targetDescription,
        access,
        state,
        targetAlias -> {
          RsqlCollectionQuery fieldQuery =
              new RsqlCollectionQuery(targetDescription, targetAlias, targetAlias + "f");
          return state.merge(
              fieldQuery.translateNested(
                  new FilterExpression.Comparison(
                      path.targetField(),
                      comparison.operator(),
                      comparison.values(),
                      comparison.wildcard()),
                  state.enforcesComplexityLimit()));
        });
  }

  private List<String> correlation(
      Relationship<?> relationship,
      RelationshipTarget<?> target,
      CollectionDescription<?> targetDescription,
      String targetAlias,
      RsqlCompilationState state) {
    SplitReferenceBinding<?, ?, ?> binding = relationship.binding();
    List<String> conjuncts = new ArrayList<>();
    conjuncts.add(
        targetAlias
            + "."
            + targetDescription.requireField(targetDescription.idField()).property()
            + " = "
            + alias
            + "."
            + binding.idProperty());
    if (binding.hasKindProperty()) {
      conjuncts.add(alias + "." + binding.kindProperty() + " = :" + state.add(target.storedKind()));
    }
    return conjuncts;
  }

  String compileRelationship(
      FilterExpression.Comparison comparison,
      FilterSelector.RelationshipPart<?> selector,
      RsqlCompilationState state) {
    String readable = compileReadableRelationship(selector.relationship(), state);
    SplitReferenceBinding<?, ?, ?> binding = selector.relationship().binding();
    String kindPath = alias + "." + binding.kindProperty();
    String idPath = alias + "." + binding.idProperty();
    if (comparison.operator() == Operator.EXISTS) {
      return Boolean.TRUE.equals(comparison.values().get(0)) ? readable : "NOT " + readable;
    }
    String stored;
    if (selector.part() == FilterSelector.RelationshipComponent.ROOT) {
      // A single-target binding stores no kind column; the parser already matched the prefix.
      stored =
          binding.hasKindProperty()
              ? compileReference(comparison, kindPath, idPath, state)
              : compileFixedTargetReference(comparison, idPath, state);
    } else {
      String path =
          selector.part() == FilterSelector.RelationshipComponent.KIND ? kindPath : idPath;
      Operator operator = comparison.operator();
      if (operator == Operator.IN || operator == Operator.NOT_IN) {
        String parameter = state.add(comparison.values());
        stored = path + (operator == Operator.IN ? " IN :" : " NOT IN :") + parameter;
      } else {
        stored = path + " " + symbol(operator) + " :" + state.add(comparison.values().get(0));
      }
    }
    return "(" + readable + " AND " + stored + ")";
  }

  String compileReadableRelationship(Relationship<?> relationship, RsqlCompilationState state) {
    List<String> readableTargets = new ArrayList<>();
    for (RelationshipTarget<?> target : relationship.targets()) {
      CollectionDescription<?> targetDescription = state.targets.description(target.resourceName());
      if (targetDescription == null) {
        continue;
      }
      AccessResult access = state.targets.result(target.resourceName());
      if (access.isDenied()) {
        continue;
      }
      readableTargets.add(
          readableTargetExists(relationship, target, targetDescription, access, state, null));
    }
    return readableTargets.isEmpty() ? "(1 = 0)" : "(" + String.join(" OR ", readableTargets) + ")";
  }

  /** A safe target reference does not grant access to its delegated runtime field source. */
  String compileReadableRuntimeSource(
      Relationship<?> relationship, String resourceName, RsqlCompilationState state) {
    CollectionDescription<?> source = state.targets.description(resourceName);
    AccessResult access = state.targets.result(resourceName);
    if (source == null || access.isDenied()) {
      return "(1 = 0)";
    }
    return readableTargetExists(
        relationship, relationship.targets().get(0), source, access, state, null);
  }

  private String readableTargetExists(
      Relationship<?> relationship,
      RelationshipTarget<?> target,
      CollectionDescription<?> targetDescription,
      AccessResult access,
      RsqlCompilationState state,
      Function<String, String> additionalPredicate) {
    String targetAlias = state.nextTargetAlias();
    List<String> conjuncts =
        correlation(relationship, target, targetDescription, targetAlias, state);
    Map<String, RsqlCollectionQuery.Subquery> accessSubqueries = new LinkedHashMap<>();
    if (additionalPredicate != null) {
      conjuncts.add(additionalPredicate.apply(targetAlias));
    }
    access
        .constraintOrEmpty()
        .ifPresent(
            constraint -> {
              RsqlCollectionQuery.Predicate accessPredicate =
                  new RsqlCollectionQuery(targetDescription, targetAlias, targetAlias + "a")
                      .translateTrusted(constraint);
              state.parameters.putAll(accessPredicate.parameters());
              accessSubqueries.putAll(accessPredicate.subqueries());
              conjuncts.add(accessPredicate.expression());
            });
    return "EXISTS "
        + state.addSubquery(
            new RsqlCollectionQuery.Subquery(
                targetDescription.entityType(),
                targetAlias,
                String.join(" AND ", conjuncts),
                accessSubqueries));
  }

  private static String compileFixedTargetReference(
      FilterExpression.Comparison comparison, String idPath, RsqlCompilationState state) {
    List<Object> ids = new ArrayList<>();
    for (Object value : comparison.values()) {
      if (!(value instanceof ResourceReference<?, ?> reference)) {
        throw new CollectionQueryException(CollectionQueryException.Reason.VALUE);
      }
      ids.add(reference.id());
    }
    String stored = "(" + idPath + " IN :" + state.add(ids) + ")";
    return switch (comparison.operator()) {
      case EQUAL, IN -> stored;
      case NOT_EQUAL, NOT_IN -> "NOT " + stored;
      default -> throw new CollectionQueryException(CollectionQueryException.Reason.OPERATOR);
    };
  }

  private String compileReference(
      FilterExpression.Comparison comparison,
      String kindPath,
      String idPath,
      RsqlCompilationState state) {
    Operator operator = comparison.operator();
    if (operator == Operator.EXISTS) {
      boolean exists = Boolean.TRUE.equals(comparison.values().get(0));
      return exists
          ? "(" + kindPath + " IS NOT NULL AND " + idPath + " IS NOT NULL)"
          : "(" + kindPath + " IS NULL OR " + idPath + " IS NULL)";
    }
    String pairs =
        "("
            + comparison.values().stream()
                .map(value -> referencePair(value, kindPath, idPath, state))
                .reduce((left, right) -> left + " OR " + right)
                .orElseThrow()
            + ")";
    return switch (operator) {
      case EQUAL, IN -> pairs;
      case NOT_EQUAL, NOT_IN -> "NOT " + pairs;
      default -> throw new CollectionQueryException(CollectionQueryException.Reason.OPERATOR);
    };
  }
}
