package com.researchspace.dao.query;

import com.researchspace.core.util.Utf8Hex;
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
  private final RsqlFieldPredicateCompilerRegistry compilers;

  RsqlRuntimeFieldCompiler(
      CollectionDescription<?> description,
      String alias,
      RsqlRelationshipCompiler relationships,
      RsqlFieldPredicateCompilerRegistry compilers) {
    this.description = description;
    this.compilers = compilers;
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
    compilers.validate(field.type());
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
            (property, value) -> {
              String path = valueAlias + "." + property;
              Object matchValue = value;
              if (value instanceof String text) {
                path = RsqlSqlFragments.exactTextIdentity(path);
                matchValue = Utf8Hex.encode(text);
              }
              conjuncts.add(path + " = :" + state.add(matchValue));
            });
    // Only EXISTS false negates the value-row EXISTS. Inequality and NOT_IN still require a
    // non-null, nonempty stored value.
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
    // Keep target and delegated-source readability outside the value-row negation so an
    // unreadable target or source cannot satisfy a missing-value filter.
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
        + compilers
            .require(field.type().comparisonStrategy())
            .compile(comparison, valuePath, state);
  }
}
