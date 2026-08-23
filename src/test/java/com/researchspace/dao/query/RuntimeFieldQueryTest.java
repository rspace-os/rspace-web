package com.researchspace.dao.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.dao.query.RsqlCollectionQuery.Predicate;
import com.researchspace.dao.query.RsqlCollectionQuery.Subquery;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResolvedRuntimeField;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.RuntimeFieldBinding;
import com.researchspace.model.collection.RuntimeFieldDefinition;
import com.researchspace.model.collection.RuntimeFieldSelection;
import com.researchspace.model.collection.RuntimeFieldValueType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.field.InventoryEntityField;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeFieldQueryTest {

  private static final RsqlCollectionQuery TRANSLATOR =
      new RsqlCollectionQuery(ApiV2InstrumentResource.DESCRIPTION, "item");

  private static ResolvedRuntimeField field(String id, RuntimeFieldValueType type) {
    Map<String, Object> match = new LinkedHashMap<>();
    match.put("templateField.id", Long.valueOf(id.substring(2)));
    match.put("deleted", false);
    return new ResolvedRuntimeField(
        new RuntimeFieldDefinition(
            id, "customFields." + id, "Hazard class", type, "IT9", "Template", List.of()),
        new RuntimeFieldBinding(InventoryEntityField.class, "instrumentEntity.id", "data", match));
  }

  private static Predicate translate(
      String id, RuntimeFieldValueType type, Operator operator, List<Object> values) {
    ResolvedRuntimeField resolved = field(id, type);
    RuntimeFieldSelection selection =
        new RuntimeFieldSelection(Map.of(resolved.selector(), resolved), java.util.Set.of());
    return TRANSLATOR.translate(
        new FilterExpression.Comparison(resolved.selector(), operator, values, false),
        RelationshipReadAccess.none(),
        selection);
  }

  private static RelationshipReadAccess targetAccess() {
    AccessFunction readAccess =
        AccessFunction.documented(
            "Test relationship target access.",
            java.util.Set.of(),
            ignored ->
                AccessResult.allowedWhere(
                    new FilterExpression.Comparison(
                        "deleted", Operator.EQUAL, List.of(false), false)));
    return RelationshipReadAccess.forActor(
        new ResourceRegistry(
            List.of(
                ApiV2BookingConfigurationResource.DESCRIPTION,
                ApiV2UserResource.DESCRIPTION,
                ApiV2InstrumentResource.description(readAccess))),
        null);
  }

  private static Subquery onlySubquery(Predicate predicate) {
    assertEquals(1, predicate.subqueries().size());
    return predicate.subqueries().values().iterator().next();
  }

  @Test
  void correlatesOneExistsSubqueryAgainstTheValueTable() {
    Predicate predicate =
        translate("SF104", RuntimeFieldValueType.TEXT, Operator.EQUAL, List.of("BSL-2"));

    Subquery subquery = onlySubquery(predicate);
    assertEquals(InventoryEntityField.class, subquery.entityType());
    assertTrue(predicate.expression().startsWith("EXISTS "));
    assertEquals(
        "rsqlTarget0.instrumentEntity.id = item.id"
            + " AND rsqlTarget0.templateField.id = :rsql0"
            + " AND rsqlTarget0.deleted = :rsql1"
            + " AND (rsqlTarget0.data IS NOT NULL AND rsqlTarget0.data <> '')"
            + " AND rsqlTarget0.data = :rsql2",
        subquery.whereExpression());
    assertEquals(Map.of("rsql0", 104L, "rsql1", false, "rsql2", "BSL-2"), predicate.parameters());
  }

  @Test
  void convertsANumericComparisonRatherThanComparingItAsText() {
    Predicate predicate =
        translate(
            "SF108", RuntimeFieldValueType.NUMBER, Operator.LESS_THAN_OR_EQUAL, List.of(-80d));

    assertTrue(
        onlySubquery(predicate)
            .whereExpression()
            .endsWith("NUMERIC_TEXT(rsqlTarget0.data) <= :rsql2"),
        onlySubquery(predicate).whereExpression());
    assertEquals(-80d, predicate.parameters().get("rsql2"));
  }

  @Test
  void comparesADateAsStoredIsoTextSoTheIndexStillApplies() {
    Predicate predicate =
        translate("SF3", RuntimeFieldValueType.DATE, Operator.GREATER_THAN, List.of("2026-01-31"));

    assertTrue(
        onlySubquery(predicate).whereExpression().endsWith("rsqlTarget0.data > :rsql2"),
        onlySubquery(predicate).whereExpression());
  }

  @Test
  void matchesOneQuotedOptionInsideTheStoredChoiceArray() {
    Predicate predicate =
        translate("SF7", RuntimeFieldValueType.CHOICE, Operator.CONTAINS, List.of("BSL-2"));

    assertTrue(
        onlySubquery(predicate)
            .whereExpression()
            .endsWith("(rsqlTarget0.data LIKE :rsql2 ESCAPE '!')"),
        onlySubquery(predicate).whereExpression());
    assertEquals("%\"BSL-2\"%", predicate.parameters().get("rsql2"));
  }

  @Test
  void treatsAnEmptyValueAsNoValueForExists() {
    Predicate present =
        translate("SF104", RuntimeFieldValueType.TEXT, Operator.EXISTS, List.of(true));
    Predicate absent =
        translate("SF104", RuntimeFieldValueType.TEXT, Operator.EXISTS, List.of(false));

    assertTrue(
        onlySubquery(present)
            .whereExpression()
            .endsWith("(rsqlTarget0.data IS NOT NULL AND rsqlTarget0.data <> '')"));
    assertTrue(absent.expression().startsWith("(NOT EXISTS "));
  }

  @Test
  void compilesAFieldReachedThroughARelationshipWithoutNestingSubqueries() {
    ResolvedRuntimeField resolved = field("SF104", RuntimeFieldValueType.TEXT);
    String selector = "target." + resolved.selector();
    Predicate predicate =
        new RsqlCollectionQuery(ApiV2BookingConfigurationResource.DESCRIPTION, "cfg")
            .translate(
                new FilterExpression.Comparison(selector, Operator.EQUAL, List.of("BSL-2"), false),
                targetAccess(),
                new RuntimeFieldSelection(
                    Map.of(selector, resolved), java.util.Set.of(), Map.of(selector, "target")));

    assertEquals(2, predicate.subqueries().size());
    predicate
        .subqueries()
        .forEach(
            (name, subquery) ->
                predicate
                    .subqueries()
                    .keySet()
                    .forEach(
                        other ->
                            assertFalse(
                                subquery.whereExpression().contains(other),
                                "a subquery body cannot reference another subquery by name")));
    Subquery values =
        predicate.subqueries().values().stream()
            .filter(subquery -> subquery.entityType() == InventoryEntityField.class)
            .findFirst()
            .orElseThrow();
    assertTrue(
        values.whereExpression().startsWith("rsqlTarget1.instrumentEntity.id = cfg.target.id"),
        values.whereExpression());
    Subquery readable =
        predicate.subqueries().values().stream()
            .filter(subquery -> subquery.entityType() == Instrument.class)
            .findFirst()
            .orElseThrow();
    assertTrue(
        readable.whereExpression().contains(".deleted = :"),
        "the target's own read rule must still be repeated: " + readable.whereExpression());
  }

  @Test
  void refusesAnOperatorTheDefinitionDoesNotPublish() {
    assertThrows(
        CollectionQueryException.class,
        () -> translate("SF7", RuntimeFieldValueType.CHOICE, Operator.LIKE, List.of("x")));
  }

  @Test
  void keepsARuntimePredicateAndAStaticOneInTheSameQuery() {
    ResolvedRuntimeField resolved = field("SF104", RuntimeFieldValueType.TEXT);
    Predicate predicate =
        TRANSLATOR.translate(
            new FilterExpression.And(
                List.of(
                    new FilterExpression.Comparison(
                        "name", Operator.CONTAINS, List.of("scope"), false),
                    new FilterExpression.Comparison(
                        resolved.selector(), Operator.EQUAL, List.of("BSL-2"), false))),
            RelationshipReadAccess.none(),
            new RuntimeFieldSelection(Map.of(resolved.selector(), resolved), java.util.Set.of()));

    assertTrue(predicate.expression().contains("LOWER(item.editInfo.name) LIKE"));
    assertTrue(predicate.expression().contains("EXISTS "));
    assertEquals(1, predicate.subqueries().size());
  }
}
