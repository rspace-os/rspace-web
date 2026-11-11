package com.researchspace.dao.query;

import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldType;
import com.researchspace.model.collection.FieldComparisonStrategy;
import com.researchspace.model.collection.FilterSelector;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.RuntimeFieldValueType;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable explicit mappings from model comparison identities to trusted SQL adapters. */
public final class RsqlFieldPredicateCompilerRegistry {
  public record Registration(
      FieldComparisonStrategy strategy, RsqlFieldPredicateCompiler compiler) {
    public Registration {
      Objects.requireNonNull(strategy, "Comparison strategy");
      Objects.requireNonNull(compiler, "Comparison compiler");
      if (strategy.equals(FieldComparisonStrategy.NONE)) {
        throw new IllegalArgumentException("Non-queryable types must have no comparison compiler");
      }
    }
  }

  private static final RsqlFieldPredicateCompilerRegistry BUILT_INS =
      new RsqlFieldPredicateCompilerRegistry(
          List.of(
              new Registration(FieldComparisonStrategy.SCALAR, new RsqlScalarPredicateCompiler()),
              new Registration(
                  FieldComparisonStrategy.NUMERIC_TEXT, new RsqlNumericTextPredicateCompiler()),
              new Registration(
                  FieldComparisonStrategy.CHOICE_TEXT, new RsqlChoicePredicateCompiler())));

  private final Map<FieldComparisonStrategy, RsqlFieldPredicateCompiler> compilers;

  public RsqlFieldPredicateCompilerRegistry(Collection<Registration> registrations) {
    Map<FieldComparisonStrategy, RsqlFieldPredicateCompiler> mapped = new LinkedHashMap<>();
    Objects.requireNonNull(registrations, "Compiler registrations")
        .forEach(
            registration -> {
              Objects.requireNonNull(registration, "Compiler registration");
              if (mapped.putIfAbsent(registration.strategy(), registration.compiler()) != null) {
                throw new IllegalArgumentException(
                    "Duplicate comparison compiler: " + registration.strategy().id());
              }
            });
    compilers = Map.copyOf(mapped);
  }

  public static RsqlFieldPredicateCompilerRegistry builtIns() {
    return BUILT_INS;
  }

  /** Returns immutable registrations for explicitly extending a built-in registry. */
  public List<Registration> registrations() {
    return compilers.entrySet().stream()
        .map(entry -> new Registration(entry.getKey(), entry.getValue()))
        .toList();
  }

  /** Rejects invalid public declarations before starting an operation. */
  public void validate(CollectionDescription<?> description) {
    description.fields().forEach(field -> validate(field.type()));
    description
        .publicFilterSelectors()
        .forEach(
            selector -> {
              if (selector instanceof FilterSelector.Property<?> property) {
                validate(property.type());
              } else if (selector instanceof FilterSelector.RuntimeField<?> runtime) {
                validate(runtime.field().type());
              }
            });
  }

  public void validate(CollectionFieldType<?> type) {
    validate(type.comparisonStrategy(), type.operators());
  }

  public void validate(RuntimeFieldValueType type) {
    validate(type.comparisonStrategy(), type.operators());
  }

  private void validate(FieldComparisonStrategy strategy, Set<Operator> operators) {
    Objects.requireNonNull(strategy, "Comparison strategy");
    if (strategy.equals(FieldComparisonStrategy.NONE)) {
      if (!operators.isEmpty()) {
        throw new IllegalArgumentException("Non-queryable types must have no filter operators");
      }
    } else if (!operators.isEmpty()) {
      require(strategy);
    }
  }

  public RsqlFieldPredicateCompiler require(FieldComparisonStrategy strategy) {
    RsqlFieldPredicateCompiler compiler = compilers.get(strategy);
    if (compiler == null) {
      throw new IllegalArgumentException("Missing comparison compiler: " + strategy.id());
    }
    return compiler;
  }
}
