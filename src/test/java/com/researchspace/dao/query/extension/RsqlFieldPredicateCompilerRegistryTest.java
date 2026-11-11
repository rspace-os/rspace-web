package com.researchspace.dao.query.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.dao.query.RsqlCollectionQuery;
import com.researchspace.dao.query.RsqlFieldPredicateCompiler;
import com.researchspace.dao.query.RsqlFieldPredicateCompilerRegistry;
import com.researchspace.dao.query.RsqlFieldPredicateCompilerRegistry.Registration;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldType;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.FieldComparisonStrategy;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.InternalFilter;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.Relationship;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.RelationshipTarget;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.ResourceMember;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.RsqlFilterParser;
import com.researchspace.model.collection.RuntimeFieldBinding;
import com.researchspace.model.collection.RuntimeFieldDefinition;
import com.researchspace.model.collection.RuntimeFieldSelection;
import com.researchspace.model.collection.RuntimeFieldValueType;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.collection.SplitReferenceBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Lives outside the compiler package to exercise the public extension API. */
class RsqlFieldPredicateCompilerRegistryTest {
  private static final FieldComparisonStrategy CUSTOM = new FieldComparisonStrategy("test-code");
  private static final RsqlFieldPredicateCompiler CODE_COMPILER =
      (comparison, path, context) ->
          "REVERSE(" + path + ") = :" + context.add(comparison.values().get(0));

  @Test
  void snapshotsRegistrationsAndRejectsDuplicateOrNonQueryableAdapters() {
    List<Registration> registrations = new ArrayList<>();
    registrations.add(new Registration(CUSTOM, CODE_COMPILER));
    RsqlFieldPredicateCompilerRegistry registry =
        new RsqlFieldPredicateCompilerRegistry(registrations);
    registrations.clear();

    assertSame(CODE_COMPILER, registry.require(new FieldComparisonStrategy("test-code")));
    assertThrows(UnsupportedOperationException.class, () -> registry.registrations().clear());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RsqlFieldPredicateCompilerRegistry(
                List.of(
                    new Registration(CUSTOM, CODE_COMPILER),
                    new Registration(CUSTOM, CODE_COMPILER))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Registration(FieldComparisonStrategy.NONE, CODE_COMPILER));
    assertThrows(IllegalArgumentException.class, () -> new FieldComparisonStrategy(" "));
  }

  @Test
  void validatesDeclaredTypesAndSelectedInternalFilters() {
    CollectionDescription<Row> description =
        description(new CodeType(CUSTOM), AccessFunction.anyone());
    assertThrows(IllegalArgumentException.class, () -> new RsqlCollectionQuery(description, "row"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            RsqlFieldPredicateCompilerRegistry.builtIns()
                .validate(new CodeType(FieldComparisonStrategy.NONE)));

    CollectionDescription<Row> internal =
        new CollectionDescription<>(
            "internal",
            Row.class,
            List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Row::id)),
            "id",
            List.of(new Sort("id", true)),
            AccessPolicy.authenticated(),
            List.of(new InternalFilter("secret", "code", new CodeType(CUSTOM))));
    RsqlCollectionQuery query = new RsqlCollectionQuery(internal, "row");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            query.translateTrusted(
                new FilterExpression.Comparison(
                    "secret", Operator.EQUAL, List.of("value"), false)));
  }

  @Test
  void selectsCustomParsingAndSqlWithoutSharingOperationState() {
    CollectionDescription<Row> description =
        description(new CodeType(CUSTOM), AccessFunction.anyone());
    RsqlCollectionQuery query = new RsqlCollectionQuery(description, "row", customRegistry());
    FilterExpression filter = new RsqlFilterParser(description).parse("code==code:abc");

    List<RsqlCollectionQuery.Predicate> results =
        IntStream.range(0, 20).parallel().mapToObj(ignored -> query.translate(filter)).toList();

    results.forEach(
        result -> {
          assertEquals("REVERSE(row.code) = :rsql0", result.expression());
          assertEquals(Map.of("rsql0", "abc"), result.parameters());
        });
    RsqlCollectionQuery.Compilation compilation = query.newCompilation();
    assertEquals(
        Map.of("rsql0", "abc"),
        compilation.translate(filter, RelationshipReadAccess.none()).parameters());
    assertEquals(
        Map.of("rsql1", "abc"),
        compilation.translate(filter, RelationshipReadAccess.none()).parameters());
  }

  @Test
  void propagatesCustomRegistryIntoTargetFiltersAndTargetAccessConstraints() {
    AccessFunction access =
        AccessFunction.documented(
            "Requires a test code.",
            Set.of(),
            ignored ->
                AccessResult.allowedWhere(
                    new FilterExpression.Comparison(
                        "code", Operator.EQUAL, List.of("allowed"), false)));
    CollectionDescription<Row> target = description(new CodeType(CUSTOM), access);
    CollectionDescription<Source> source =
        new CollectionDescription<>(
            "sources",
            Source.class,
            List.<ResourceMember<Source>>of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Source::id),
                Relationship.polymorphicToOne(
                    "target",
                    CollectionFieldTypes.longNumber(),
                    List.of(new RelationshipTarget<>("rows", "rows", "RW", Row.class)),
                    SplitReferenceBinding.monomorphic(Source::target, "targetId"))),
            "id",
            List.of(new Sort("id", true)));
    ResourceRegistry resources = new ResourceRegistry(List.of(source, target));
    RelationshipReadAccess targets = RelationshipReadAccess.forActor(resources, null);
    RsqlCollectionQuery query = new RsqlCollectionQuery(source, "source", customRegistry());
    FilterExpression filter =
        new RsqlFilterParser(source, resources).parse("target.code==code:abc");

    RsqlCollectionQuery.Predicate result = query.translate(filter, targets);
    RsqlCollectionQuery.Subquery subquery = result.subqueries().values().iterator().next();
    assertEquals(
        "rsqlTarget0.id = source.targetId AND REVERSE(rsqlTarget0.code) = :rsql0"
            + " AND REVERSE(rsqlTarget0.code) = :rsql1",
        subquery.whereExpression());
    assertEquals(Map.of("rsql0", "abc", "rsql1", "allowed"), result.parameters());

    RsqlCollectionQuery.Predicate readable = query.compileReadableRelationship("target", targets);
    assertTrue(
        readable
            .subqueries()
            .values()
            .iterator()
            .next()
            .whereExpression()
            .contains("REVERSE(rsqlTarget0.code) = :rsql0"));
    assertEquals(Map.of("rsql0", "allowed"), readable.parameters());
  }

  @Test
  void missingRuntimeAdapterFailsEvenForPresenceOnlyComparisons() {
    List<Registration> registrations =
        RsqlFieldPredicateCompilerRegistry.builtIns().registrations().stream()
            .filter(
                registration ->
                    !registration.strategy().equals(FieldComparisonStrategy.NUMERIC_TEXT))
            .toList();
    CollectionDescription<Row> description =
        description(new CodeType(FieldComparisonStrategy.SCALAR), AccessFunction.anyone());
    RsqlCollectionQuery query =
        new RsqlCollectionQuery(
            description, "row", new RsqlFieldPredicateCompilerRegistry(registrations));
    ResolvedRuntimeField field =
        new ResolvedRuntimeField(
            new RuntimeFieldDefinition(
                "N1", "custom.N1", "Number", RuntimeFieldValueType.NUMBER, null, null, List.of()),
            new RuntimeFieldBinding(Row.class, "parentId", "code", Map.of()));
    RuntimeFieldSelection selection =
        new RuntimeFieldSelection(Map.of(field.selector(), field), Set.of());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            query.translate(
                new FilterExpression.Comparison(
                    field.selector(), Operator.EXISTS, List.of(true), false),
                RelationshipReadAccess.none(),
                selection));
    for (RuntimeFieldValueType type : RuntimeFieldValueType.values()) {
      RsqlFieldPredicateCompilerRegistry.builtIns().validate(type);
    }
  }

  private static RsqlFieldPredicateCompilerRegistry customRegistry() {
    List<Registration> registrations =
        new ArrayList<>(RsqlFieldPredicateCompilerRegistry.builtIns().registrations());
    registrations.add(new Registration(CUSTOM, CODE_COMPILER));
    return new RsqlFieldPredicateCompilerRegistry(registrations);
  }

  private static CollectionDescription<Row> description(
      CollectionFieldType<String> type, AccessFunction readAccess) {
    return new CollectionDescription<>(
        "rows",
        Row.class,
        List.of(
            Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Row::id),
            Field.readOnly("code", "code", type, Row::code)),
        "id",
        List.of(new Sort("id", true)),
        AccessPolicy.readOnly(readAccess));
  }

  private record Row(Long id, String code) {}

  private record Source(Long id, Long targetId) {
    ResourceReference<String, Long> target() {
      return new ResourceReference<>("rows", targetId);
    }
  }

  private record CodeType(FieldComparisonStrategy comparisonStrategy)
      implements CollectionFieldType<String> {
    @Override
    public Class<String> javaType() {
      return String.class;
    }

    @Override
    public InputKind inputKind() {
      return InputKind.STRING;
    }

    @Override
    public String parse(String value) {
      if (!value.startsWith("code:")) {
        throw new IllegalArgumentException("Expected code prefix");
      }
      return value.substring(5);
    }

    @Override
    public Object serialize(String value) {
      return "code:" + value;
    }

    @Override
    public Set<Operator> operators() {
      return Set.of(Operator.EQUAL);
    }
  }
}
