package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.model.collection.CollectionFieldTypes.Constraints;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CollectionFieldTypesTest {

  @Test
  void configurableTextRetainsIdentityParsing() {
    CollectionFieldType<String> type = CollectionFieldTypes.text(Set.of(Operator.EQUAL), false);
    org.junit.jupiter.api.Assertions.assertNull(type.parse(null));
    org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> type.validate(null));
  }

  @Test
  void constrainedTextKeepsItsQueryBehaviour() {
    CollectionFieldType<String> base = CollectionFieldTypes.text(10);
    CollectionFieldType<String> constrained = CollectionFieldTypes.constrained(base, pattern("^a"));
    CollectionDescription<Item> description =
        new CollectionDescription<>(
            "items",
            Item.class,
            List.of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Item::id),
                Field.readOnly("name", "name", constrained, Item::name)),
            "id",
            List.of(new Sort("id", true)));

    FilterExpression.Comparison filter =
        assertInstanceOf(
            FilterExpression.Comparison.class,
            new RsqlFilterParser(description).parse("name==ab*"));

    assertTrue(filter.wildcard());
    assertEquals(base.sortable(), constrained.sortable());
    assertEquals(base.operators(), constrained.operators());
    assertEquals(base.schema(), constrained.schema());
  }

  @Test
  void constrainedTypeRunsTheBaseValidationFirst() {
    CollectionFieldType<String> constrained =
        CollectionFieldTypes.constrained(CollectionFieldTypes.text(3), minLength(2));

    assertThrows(IllegalArgumentException.class, () -> constrained.validate("abcd"));
    assertThrows(IllegalArgumentException.class, () -> constrained.validate("a"));
    assertDoesNotThrow(() -> constrained.validate("abc"));
  }

  @Test
  void minimumLengthCountsCharactersLikeJsonSchema() {
    CollectionFieldType<String> constrained =
        CollectionFieldTypes.constrained(CollectionFieldTypes.text(), minLength(2));

    assertThrows(IllegalArgumentException.class, () -> constrained.validate("\uD83D\uDE00"));
    assertDoesNotThrow(() -> constrained.validate("\uD83D\uDE00\uD83D\uDE00"));
  }

  @Test
  void patternMatchesAnywhereLikeJsonSchema() {
    CollectionFieldType<String> constrained =
        CollectionFieldTypes.constrained(CollectionFieldTypes.text(), pattern("[0-9]{2}"));

    assertDoesNotThrow(() -> constrained.validate("room 42 east"));
    assertThrows(IllegalArgumentException.class, () -> constrained.validate("room four"));
    assertEquals(Map.of("pattern", "[0-9]{2}"), constrained.writeConstraints());
  }

  @Test
  void numberBoundsAreInclusiveAndPublished() {
    CollectionFieldType<Integer> constrained =
        CollectionFieldTypes.constrained(
            CollectionFieldTypes.integer(),
            new Constraints(BigDecimal.ONE, BigDecimal.valueOf(7), null, null, null));

    assertDoesNotThrow(() -> constrained.validate(1));
    assertDoesNotThrow(() -> constrained.validate(7));
    assertThrows(IllegalArgumentException.class, () -> constrained.validate(0));
    assertThrows(IllegalArgumentException.class, () -> constrained.validate(8));
    assertEquals(
        Map.of("minimum", BigDecimal.ONE, "maximum", BigDecimal.valueOf(7)),
        constrained.writeConstraints());
  }

  @Test
  void allowedValuesAreEnforcedOnAWritableField() {
    Field<Item, String> state =
        Field.writable(
            "state",
            "name",
            CollectionFieldTypes.constrained(
                CollectionFieldTypes.text(),
                new Constraints(null, null, null, null, List.of("OPEN", "CLOSED"))),
            Item::name,
            (item, value) -> {});

    assertEquals("OPEN", state.validateValue("OPEN"));
    assertThrows(IllegalArgumentException.class, () -> state.validateValue("PENDING"));
    assertEquals(
        Map.of("enum", List.of("OPEN", "CLOSED")),
        state.schema(AccessPolicy.authenticated()).writeConstraints());
  }

  @Test
  void rejectsConstraintsThatDoNotFitTheBase() {
    CollectionFieldType<String> constrained =
        CollectionFieldTypes.constrained(CollectionFieldTypes.text(), minLength(1));

    assertThrows(
        IllegalArgumentException.class,
        () -> CollectionFieldTypes.constrained(constrained, pattern("a")));
    assertThrows(
        IllegalArgumentException.class,
        () -> CollectionFieldTypes.constrained(CollectionFieldTypes.object(), minLength(1)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CollectionFieldTypes.constrained(
                CollectionFieldTypes.array(
                    Item.class,
                    List.of(Field.embedded("name", CollectionFieldTypes.text(), Item::name)),
                    values -> new Item(null, (String) values.get("name"))),
                minLength(1)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CollectionFieldTypes.constrained(
                CollectionFieldTypes.structured(
                    String.class,
                    CollectionFieldType.InputKind.ARRAY,
                    Map.of(),
                    JsonNode::toString,
                    value -> value),
                minLength(1)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CollectionFieldTypes.constrained(
                CollectionFieldTypes.text(),
                new Constraints(BigDecimal.ONE, null, null, null, null)));
    assertThrows(
        IllegalArgumentException.class,
        () -> CollectionFieldTypes.constrained(CollectionFieldTypes.longNumber(), pattern("1")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CollectionFieldTypes.constrained(
                CollectionFieldTypes.enumeration(State.class),
                new Constraints(null, null, null, null, List.of("OPEN"))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Constraints(BigDecimal.TEN, BigDecimal.ONE, null, null, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Constraints(
                null, null, null, Pattern.compile("^a$", Pattern.CASE_INSENSITIVE), null));
  }

  @Test
  void enumerationPublishesItsConstants() {
    CollectionFieldType<State> type = CollectionFieldTypes.enumeration(State.class);

    assertEquals(Map.of("enum", List.of("OPEN", "CLOSED")), type.schema().structure());
    assertThrows(IllegalArgumentException.class, () -> type.parse("PENDING"));
  }

  @Test
  void textValidatesItsMaximumLengthForDefaults() {
    assertThrows(
        IllegalArgumentException.class, () -> CollectionFieldTypes.text(3).validate("abcd"));
    assertFalse(CollectionFieldTypes.text(3).writeConstraints().containsKey("maxLength"));
  }

  @Test
  void scalarCompatibilityKeepsLiteralParsingSeparateFromWriteRules() {
    CollectionFieldType<Integer> constrained =
        CollectionFieldTypes.constrained(
            CollectionFieldTypes.integer(),
            new Constraints(BigDecimal.ONE, BigDecimal.TEN, null, null, null));
    assertEquals(0, constrained.parse("0"));
    assertThrows(IllegalArgumentException.class, () -> constrained.validate(0));
    assertThrows(IllegalArgumentException.class, () -> CollectionFieldTypes.bool().parse("TRUE"));
    assertThrows(
        IllegalArgumentException.class, () -> CollectionFieldTypes.integer().parse("2147483648"));
    assertThrows(
        IllegalArgumentException.class,
        () -> CollectionFieldTypes.longNumber().parse("9223372036854775808"));
    assertEquals(2, CollectionFieldTypes.text(2).parse("\uD83D\uDE00").length());
    assertThrows(
        IllegalArgumentException.class, () -> CollectionFieldTypes.text(1).parse("\uD83D\uDE00"));
  }

  @Test
  void publicImplementationsRetainDeclarationGuards() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new com.researchspace.model.collection.types.ConstrainedFieldType<>(
                CollectionFieldTypes.object(), minLength(1).toFieldConstraints()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new com.researchspace.model.collection.types.StructuredValueFieldType<>(
                String.class,
                CollectionFieldType.InputKind.ARRAY,
                new CollectionFieldType.Schema("array", null, null),
                JsonNode::toString,
                value -> value));
  }

  private static Constraints pattern(String regex) {
    return new Constraints(null, null, null, Pattern.compile(regex), null);
  }

  private static Constraints minLength(int length) {
    return new Constraints(null, null, length, null, null);
  }

  private enum State {
    OPEN,
    CLOSED
  }

  private record Item(Long id, String name) {}
}
