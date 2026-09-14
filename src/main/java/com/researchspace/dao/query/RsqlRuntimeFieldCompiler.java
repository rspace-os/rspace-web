package com.researchspace.dao.query;

import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.Relationship;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.RuntimeFieldBinding;
import java.util.ArrayList;
import java.util.List;

/** Correlates runtime values and enforces presence semantics independently of value comparison. */
final class RsqlRuntimeFieldCompiler {
  private final CollectionDescription<?> description;
  private final String alias;
  private final RsqlRelationshipCompiler relationships;

  RsqlRuntimeFieldCompiler(
      CollectionDescription<?> description, String alias, RsqlRelationshipCompiler relationships) {
    this.description = description;
    this.alias = alias;
    this.relationships = relationships;
  }

  String compileRuntimeField(
      FilterExpression.Comparison comparison,
      ResolvedRuntimeField field,
      RsqlCompilationState state) {
    return compileRuntimeField(
        comparison,
        field,
        alias + "." + description.requireField(description.idField()).property(),
        state);
  }

  private String compileRuntimeField(
      FilterExpression.Comparison comparison,
      ResolvedRuntimeField field,
      String parentIdPath,
      RsqlCompilationState state) {
    Operator operator = comparison.operator();
    if (!field.definition().operators().contains(operator)) {
      throw new CollectionQueryException(CollectionQueryException.Reason.OPERATOR);
    }
    RuntimeFieldBinding binding = field.binding();
    String valueAlias = state.nextTargetAlias();
    List<String> conjuncts = new ArrayList<>();
    conjuncts.add(valueAlias + "." + binding.parentIdProperty() + " = " + parentIdPath);
    binding
        .match()
        .forEach(
            (property, value) ->
                conjuncts.add(valueAlias + "." + property + " = :" + state.add(value)));
    boolean negated =
        operator == Operator.EXISTS && !Boolean.TRUE.equals(comparison.values().get(0));
    String valuePath = valueAlias + "." + binding.valueProperty();
    conjuncts.add(runtimeValuePredicate(comparison, field, valuePath, state));
    String exists =
        "EXISTS "
            + state.addSubquery(
                new RsqlCollectionQuery.Subquery(
                    binding.valueEntityType(), valueAlias, String.join(" AND ", conjuncts)));
    return negated ? "(NOT " + exists + ")" : exists;
  }

  String compileRuntimeFieldThroughRelationship(
      FilterExpression.Comparison comparison,
      ResolvedRuntimeField field,
      String relationshipName,
      RsqlCompilationState state) {
    Relationship<?> relationship = description.requireRelationship(relationshipName);
    if (relationship.targets().size() != 1) {
      throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
    }
    String readable = relationships.compileReadableRelationship(relationship, state);
    String parentIdPath = alias + "." + relationship.binding().idProperty();
    return "("
        + readable
        + " AND "
        + compileRuntimeField(comparison, field, parentIdPath, state)
        + ")";
  }

  private String runtimeValuePredicate(
      FilterExpression.Comparison comparison,
      ResolvedRuntimeField field,
      String valuePath,
      RsqlCompilationState state) {
    Operator operator = comparison.operator();
    String present = "(" + valuePath + " IS NOT NULL AND " + valuePath + " <> '')";
    if (operator == Operator.EXISTS) {
      return present;
    }
    return present
        + " AND "
        + RsqlFieldPredicateCompilerRegistry.runtime(field.type())
            .compile(comparison, valuePath, state);
  }
}
