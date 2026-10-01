package com.researchspace.dao.query;

import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.FilterSelector;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.QueryConstraint;
import com.researchspace.model.collection.ResolvedRuntimeField;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/** Traverses filter and trusted-constraint trees and routes selectors to their storage compiler. */
final class RsqlExpressionCompiler {
  private final CollectionDescription<?> description;
  private final String alias;
  private final RsqlRelationshipCompiler relationships;
  private final RsqlRuntimeFieldCompiler runtimeFields;

  RsqlExpressionCompiler(
      CollectionDescription<?> description, String alias, RsqlRelationshipCompiler relationships) {
    this.description = description;
    this.alias = alias;
    this.relationships = relationships;
    this.runtimeFields = new RsqlRuntimeFieldCompiler(description, alias, relationships);
  }

  String compile(FilterExpression filter, RsqlCompilationState state) {
    if (filter instanceof FilterExpression.Comparison comparison) {
      return compileComparison(comparison, state);
    }
    if (filter instanceof FilterExpression.And and) {
      return compileLogical(and.children(), " AND ", state);
    }
    if (filter instanceof FilterExpression.Or or) {
      return compileLogical(or.children(), " OR ", state);
    }
    throw new IllegalStateException("Unsupported filter expression " + filter.getClass());
  }

  String compileConstraint(QueryConstraint constraint, RsqlCompilationState state) {
    if (constraint instanceof FilterExpression filter) {
      return compile(filter, state);
    }
    if (constraint instanceof QueryConstraint.And and) {
      return compileConstraintLogical(and.children(), " AND ", state);
    }
    if (constraint instanceof QueryConstraint.Or or) {
      return compileConstraintLogical(or.children(), " OR ", state);
    }
    throw new IllegalStateException("Unsupported query constraint " + constraint.getClass());
  }

  private String compileConstraintLogical(
      List<QueryConstraint> children, String separator, RsqlCompilationState state) {
    if (children.isEmpty()) {
      throw new NoSuchElementException();
    }
    return "("
        + children.stream()
            .map(child -> compileConstraint(child, state))
            .collect(Collectors.joining(separator))
        + ")";
  }

  private String compileLogical(
      List<FilterExpression> children, String separator, RsqlCompilationState state) {
    if (children.isEmpty()) {
      throw new CollectionQueryException(CollectionQueryException.Reason.SYNTAX);
    }
    return "("
        + children.stream()
            .map(child -> compile(child, state))
            .collect(Collectors.joining(separator))
        + ")";
  }

  private String compileComparison(
      FilterExpression.Comparison comparison, RsqlCompilationState state) {
    ResolvedRuntimeField runtimeField = state.runtime.find(comparison.field());
    if (runtimeField != null) {
      String relationship = state.runtime.relationshipFor(comparison.field());
      return relationship == null
          ? runtimeFields.compileRuntimeField(comparison, runtimeField, state)
          : runtimeFields.compileRuntimeFieldThroughRelationship(
              comparison, runtimeField, relationship, state);
    }
    FilterSelector<?> selector = description.findFilterSelector(comparison.field()).orElse(null);
    if (selector == null) {
      return relationships.compileRelationshipField(comparison, state);
    }
    Operator operator = comparison.operator();
    if (!selector.operators().contains(operator)) {
      throw new CollectionQueryException(CollectionQueryException.Reason.OPERATOR);
    }
    if (selector instanceof FilterSelector.RelationshipPart<?> relationship) {
      return relationships.compileRelationship(comparison, relationship, state);
    }
    if (selector instanceof FilterSelector.Property<?> property) {
      return compileProperty(comparison, property, state);
    }
    if (selector instanceof FilterSelector.RuntimeField<?> runtime) {
      return runtimeFields.compileRuntimeField(comparison, runtime.field(), state);
    }
    throw new IllegalStateException("Unsupported filter selector " + selector.getClass());
  }

  private String compileProperty(
      FilterExpression.Comparison comparison,
      FilterSelector.Property<?> property,
      RsqlCompilationState state) {
    Operator operator = comparison.operator();
    String path = alias + "." + property.property();
    if (operator == Operator.EXISTS) {
      return path + (Boolean.TRUE.equals(comparison.values().get(0)) ? " IS NOT NULL" : " IS NULL");
    }
    return RsqlFieldPredicateCompilerRegistry.scalar().compile(comparison, path, state);
  }
}
