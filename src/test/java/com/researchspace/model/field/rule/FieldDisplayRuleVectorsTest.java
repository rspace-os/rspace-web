package com.researchspace.model.field.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.field.FieldType;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the vectors shared with the TypeScript implementation (displayRuleVectors.test.ts), so that
 * the server and the browser agree on every case.
 */
class FieldDisplayRuleVectorsTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final JsonNode VECTORS = loadVectors();

  private static JsonNode loadVectors() {
    try (InputStream in =
        FieldDisplayRuleVectorsTest.class.getResourceAsStream(
            "/fieldrules/display-rule-vectors.json")) {
      return new ObjectMapper().readTree(in);
    } catch (IOException e) {
      throw new IllegalStateException("Could not read display rule vectors", e);
    }
  }

  /**
   * Generated rather than a vector: a chain far deeper than any real template, listed dependent
   * first, must resolve without exhausting the stack (the TypeScript test does the same).
   */
  @Test
  void deepChainListedDependentFirstIsAllShownAndValid() {
    int length = 20_000;
    List<FieldSnapshot> fields = new ArrayList<>(length);
    for (int i = 0; i < length; i++) {
      FieldDisplayRule rule =
          i == length - 1
              ? null
              : new FieldDisplayRule(chainKey(i + 1), ConditionOperator.IS_ANY_OF, List.of("Yes"));
      fields.add(
          new FieldSnapshot(chainKey(i), FieldType.RADIO, rule, List.of("Yes"), List.of("Yes")));
    }

    assertEquals(Collections.nCopies(length, true), FieldDisplayRuleEvaluator.visibility(fields));
    assertEquals(List.of(), FieldDisplayRuleValidator.validate(fields));
  }

  private static String chainKey(int i) {
    return String.format(Locale.ROOT, "00000000-0000-4000-8000-%012d", i);
  }

  @TestFactory
  Stream<DynamicTest> validRulesSerialiseToTheCanonicalJsonValue() {
    // Compared as JSON values, not strings: each side serialises with its own JSON library.
    return cases("rules", "valid")
        .map(
            vector ->
                DynamicTest.dynamicTest(
                    vector.get("name").asText(),
                    () ->
                        assertEquals(
                            JSON.readTree(vector.get("canonical").asText()),
                            JSON.readTree(
                                FieldDisplayRule.fromJson(vector.get("json").asText()).toJson()))));
  }

  @TestFactory
  Stream<DynamicTest> invalidRulesAreRejected() {
    return cases("rules", "invalid")
        .map(
            vector ->
                DynamicTest.dynamicTest(
                    vector.get("name").asText(),
                    () ->
                        assertThrows(
                            IllegalArgumentException.class,
                            () -> FieldDisplayRule.fromJson(vector.get("json").asText()))));
  }

  @TestFactory
  Stream<DynamicTest> visibility() {
    return cases("visibility")
        .map(
            vector ->
                DynamicTest.dynamicTest(
                    vector.get("name").asText(),
                    () -> {
                      List<Boolean> expected = new ArrayList<>();
                      vector.get("expected").forEach(node -> expected.add(node.asBoolean()));
                      assertEquals(
                          expected,
                          FieldDisplayRuleEvaluator.visibility(fields(vector.get("fields"))));
                    }));
  }

  @TestFactory
  Stream<DynamicTest> validation() {
    return cases("validation")
        .map(
            vector ->
                DynamicTest.dynamicTest(
                    vector.get("name").asText(),
                    () ->
                        assertEquals(
                            ruleErrors(vector.get("expectedErrors")),
                            FieldDisplayRuleValidator.validate(fields(vector.get("fields"))))));
  }

  @Test
  void limitsMatchTheSharedVectors() {
    assertEquals(
        VECTORS.get("limits").get("maxValues").asInt(), FieldDisplayRuleValidator.MAX_VALUES);
    assertEquals(
        VECTORS.get("limits").get("maxJsonLength").asInt(),
        FieldDisplayRuleValidator.MAX_JSON_LENGTH);
  }

  @TestFactory
  Stream<DynamicTest> limits() {
    return cases("limits", "cases")
        .map(
            vector ->
                DynamicTest.dynamicTest(
                    vector.get("name").asText(),
                    () -> {
                      String pad =
                          vector.get("pad").asText().repeat(vector.get("padCount").asInt());
                      List<String> values = new ArrayList<>();
                      for (int i = 0; i < vector.get("valueCount").asInt(); i++) {
                        values.add(pad + String.format(Locale.ROOT, "%05d", i));
                      }
                      int tailLength = vector.get("tailLength").asInt();
                      if (tailLength > 0) {
                        values.add("x".repeat(tailLength));
                      }
                      FieldDisplayRule rule =
                          new FieldDisplayRule(chainKey(1), ConditionOperator.IS_ANY_OF, values);
                      List<FieldSnapshot> fields =
                          List.of(
                              new FieldSnapshot(
                                  chainKey(1), FieldType.RADIO, null, values, List.of()),
                              new FieldSnapshot(
                                  chainKey(2), FieldType.STRING, rule, List.of(), List.of()));

                      assertEquals(vector.get("jsonLength").asInt(), rule.toJson().length());
                      assertEquals(
                          ruleErrors(vector.get("expectedErrors")),
                          FieldDisplayRuleValidator.validate(fields));
                    }));
  }

  @TestFactory
  Stream<DynamicTest> canonicalNumbers() {
    return cases("canonicalNumbers", "cases")
        .map(
            vector ->
                DynamicTest.dynamicTest(
                    "'" + vector.get("value").asText() + "'",
                    () ->
                        assertEquals(
                            vector.get("canonical").isNull()
                                ? null
                                : vector.get("canonical").asText(),
                            RuleValues.canonicalNumber(vector.get("value").asText()))));
  }

  @TestFactory
  Stream<DynamicTest> elnChoiceData() {
    return cases("elnChoice")
        .map(
            vector ->
                DynamicTest.dynamicTest(
                    "'" + vector.get("data").asText() + "'",
                    () ->
                        assertEquals(
                            strings(vector.get("expected")),
                            ElnChoiceValues.parse(vector.get("data").asText()))));
  }

  private static Stream<JsonNode> cases(String... path) {
    JsonNode node = VECTORS;
    for (String segment : path) {
      node = node.get(segment);
    }
    return StreamSupport.stream(node.spliterator(), false);
  }

  private static List<FieldSnapshot> fields(JsonNode nodes) {
    List<FieldSnapshot> fields = new ArrayList<>();
    for (JsonNode node : nodes) {
      JsonNode key = node.get("key");
      JsonNode rule = node.get("rule");
      FieldType type = FieldType.valueOf(node.get("type").asText());
      JsonNode sourceKind = node.get("sourceKind");
      fields.add(
          new FieldSnapshot(
              key == null || key.isNull() ? null : key.asText(),
              type,
              rule == null || rule.isNull() ? null : FieldDisplayRule.fromJson(rule.toString()),
              strings(node.get("allowedOptions")),
              strings(node.get("selectedOptions")),
              sourceKind == null
                  ? SourceKind.ofType(type)
                  : SourceKind.valueOf(sourceKind.asText()),
              node.path("value").asText("")));
    }
    return fields;
  }

  private static List<FieldDisplayRuleValidator.RuleError> ruleErrors(JsonNode nodes) {
    List<FieldDisplayRuleValidator.RuleError> errors = new ArrayList<>();
    nodes.forEach(
        node ->
            errors.add(
                new FieldDisplayRuleValidator.RuleError(
                    node.get("fieldIndex").asInt(), node.get("code").asText())));
    return errors;
  }

  private static List<String> strings(JsonNode array) {
    List<String> values = new ArrayList<>();
    if (array != null) {
      array.forEach(node -> values.add(node.asText()));
    }
    return values;
  }
}
