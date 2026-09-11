package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

class InventoryOperationConfigRegistryTest {

  private final InventoryOperationConfigRegistry registry = new InventoryOperationConfigRegistry();

  @Test
  void parsesTheSevenConfiguredOperations() {
    assertEquals(
        Set.of("aliquot", "passage", "pool", "derive", "cryopreserve", "revive", "destroy"),
        registry.keys());
  }

  @Test
  void unknownOrMissingKeyResolvesToEmpty() {
    assertTrue(registry.get("aliquot").isPresent());
    assertFalse(registry.get("DERIVE").isPresent(), "keys are exact, not case-insensitive");
    assertFalse(registry.get("teleport").isPresent());
    assertFalse(registry.get(null).isPresent());
  }

  @Test
  void bindsTheFieldsTheValidatorInterprets() {
    InventoryOperationConfig cryopreserve = registry.get("cryopreserve").orElseThrow();
    InventoryOperationConfig.Input storageTemp =
        cryopreserve.inputs().stream()
            .filter(input -> "temperature".equals(input.type()))
            .findFirst()
            .orElseThrow();
    assertEquals(new BigDecimal("-18"), storageTemp.maxCelsius());
    assertNull(storageTemp.minCelsius());
    assertEquals("amountTaken", cryopreserve.effect().amountTakenFrom());
    assertEquals("storageTemp", cryopreserve.effect().storageTempFrom());
    assertEquals(
        List.of("IsDerivedFrom"),
        cryopreserve.effect().links().stream()
            .map(InventoryOperationConfig.Link::relationType)
            .toList());

    assertTrue(storageTemp.required(), "M0: a cryopreserved sample has a storage temperature");

    InventoryOperationConfig revive = registry.get("revive").orElseThrow();
    InventoryOperationConfig.Input reviveTemp =
        revive.inputs().stream()
            .filter(input -> "temperature".equals(input.type()))
            .findFirst()
            .orElseThrow();
    assertEquals(new BigDecimal("4"), reviveTemp.minCelsius());
    assertEquals(new BigDecimal("120"), reviveTemp.maxCelsius());
    // The declared defaults bind as the JSON values they are (M0 D7); the input validator types
    // them against the input's type when filling an absent input.
    assertEquals(4, reviveTemp.defaultValue());
    assertEquals(
        1,
        revive.inputs().stream()
            .filter(input -> "count".equals(input.key()))
            .findFirst()
            .orElseThrow()
            .defaultValue());
    assertNull(storageTemp.defaultValue(), "cryopreserve declares no default temperature");

    InventoryOperationConfig pool = registry.get("pool").orElseThrow();
    assertTrue(pool.requiresMultiple());
    assertFalse(pool.noOutput());

    InventoryOperationConfig destroy = registry.get("destroy").orElseThrow();
    assertTrue(destroy.noOutput());
    assertTrue(destroy.effect().emptiesOrigin());
    assertTrue(destroy.effect().links().isEmpty());
    assertNull(destroy.effect().amountTakenFrom());

    InventoryOperationConfig passage = registry.get("passage").orElseThrow();
    assertNull(passage.effect().amountTakenFrom(), "passage never decrements its origin");
    assertFalse(passage.effect().emptiesOrigin());
  }

  @Test
  void bindsTheWholeDefinitionTheStrictValidatorInterprets() {
    // The validator whitelists the request against the definition, so every part of the definition
    // it consults must bind (DevDocs/adr/0007): field-name keys (the wire identity), computed
    // values, the declared text/origin fields and the count's lower bound.
    InventoryOperationConfig aliquot = registry.get("aliquot").orElseThrow();
    assertEquals("sampleName", aliquot.effect().nameFrom());
    assertEquals("count", aliquot.effect().countFrom());
    assertNull(aliquot.effect().processNameFrom());
    assertEquals(
        List.of("operations.aliquot.linkFieldName"),
        aliquot.effect().links().stream()
            .map(InventoryOperationConfig.Link::fieldNameKey)
            .toList());
    assertEquals(
        new BigDecimal("1"),
        aliquot.inputs().stream()
            .filter(input -> "count".equals(input.key()))
            .findFirst()
            .orElseThrow()
            .min());

    InventoryOperationConfig passage = registry.get("passage").orElseThrow();
    // args bind too, since the server-side request builder sources each function argument from
    // them (plan-operations-server-builds.md, M1).
    assertEquals(
        List.of(
            new InventoryOperationConfig.Computed(
                "increment",
                "passageNumber",
                Map.of(
                    "current",
                    new InventoryOperationConfig.ArgSource(
                        "operations.passage.numberField", null, null),
                    "start",
                    new InventoryOperationConfig.ArgSource(null, null, new BigDecimal("1"))))),
        passage.effect().computed());
    assertEquals(
        List.of(
            new InventoryOperationConfig.TextField(
                "operations.passage.numberField", "passageNumber")),
        passage.effect().textFields());

    InventoryOperationConfig derive = registry.get("derive").orElseThrow();
    assertEquals("processName", derive.effect().processNameFrom());

    InventoryOperationConfig cryopreserve = registry.get("cryopreserve").orElseThrow();
    assertEquals(
        List.of(
            new InventoryOperationConfig.TextField(
                "operations.cryopreserve.cryomediumField", "cryomedium")),
        cryopreserve.effect().textFields());
    assertFalse(
        cryopreserve.inputs().stream()
            .filter(input -> "cryomedium".equals(input.key()))
            .findFirst()
            .orElseThrow()
            .required(),
        "cryomedium is optional, so its content is free text");

    InventoryOperationConfig destroy = registry.get("destroy").orElseThrow();
    assertEquals(
        List.of(new InventoryOperationConfig.Computed("today", "disposedDate")),
        destroy.effect().computed());
    assertEquals(
        List.of(
            new InventoryOperationConfig.OriginField(
                "operations.destroy.disposedField", "disposedDate", "text")),
        destroy.effect().originFields());
    assertNull(destroy.effect().nameFrom(), "a terminal operation creates no sample to name");
  }

  @Test
  void operationsWithoutTheOptionalEffectListsBindThemAsEmpty() {
    InventoryOperationConfig aliquot = registry.get("aliquot").orElseThrow();
    assertTrue(aliquot.effect().computed().isEmpty());
    assertTrue(aliquot.effect().textFields().isEmpty());
    assertTrue(aliquot.effect().originFields().isEmpty());
  }

  @Test
  void exposesTheRawConfigJsonVerbatim() throws IOException {
    // The GET /operations/config endpoint serves this string as the wizard's single source of
    // operation definitions (DevDocs/adr/0007), so it must be the file byte-for-byte, not a
    // re-serialisation of the parsed subset the backend validates with.
    String expected =
        Files.readString(Path.of("src/main/resources/inventory/operations_config.json"));
    assertEquals(expected, registry.rawConfigJson());
  }

  @Test
  void failsFastOnMissingResource() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new InventoryOperationConfigRegistry(new ClassPathResource("inventory/no-such.json")));
  }

  @Test
  void failsFastOnUnparseableResource() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new InventoryOperationConfigRegistry(
                new ByteArrayResource(
                    "not json".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
  }

  // --- semantic validation of the definitions themselves (F3) ---

  /** The registry over the given definitions, or the message of the rejection it produced. */
  private static String rejectionMessage(String json) {
    return assertThrows(
            IllegalStateException.class,
            () ->
                new InventoryOperationConfigRegistry(
                    new ByteArrayResource(json.getBytes(java.nio.charset.StandardCharsets.UTF_8))))
        .getMessage();
  }

  @Test
  void theShippedConfigPassesValidation() {
    // The build-time guard: this is what makes every check below a safety net for future config
    // edits rather than a tripwire on the file we ship today. Constructing the real registry is
    // itself the assertion, and the field initialiser above already does it for every other test.
    assertDoesNotThrow(
        () -> {
          new InventoryOperationConfigRegistry();
        });
    assertEquals(7, registry.keys().size());
  }

  @Test
  void rejectsAnInputTypeNoValidationBranchInterprets() {
    // An unrecognised type matches no validation branch, so this input's "required" and its bounds
    // would never be enforced while the definition still advertises them.
    String message =
        rejectionMessage(
            """
            [{"key":"teleport","inputs":[{"key":"destination","type":"coordinates"}]}]
            """);
    assertTrue(message.contains("operation 'teleport'"), message);
    assertTrue(message.contains("type 'coordinates'"), message);
  }

  @Test
  void rejectsAnUnknownOriginFieldType() {
    // FieldType.valueOf runs per request today, so this is a 500 on every request for the
    // operation; validating at construction turns it into a boot failure instead.
    String message =
        rejectionMessage(
            """
            [{"key":"destroy","inputs":[],"effect":{"emptiesOrigin":true,
              "computed":[{"fn":"today","into":"disposedDate"}],
              "originFields":[{"nameKey":"a.key","contentFrom":"disposedDate","type":"hologram"}]}}]
            """);
    assertTrue(message.contains("unknown field type 'hologram'"), message);
  }

  @Test
  void rejectsAContentReferenceThatNamesNothing() {
    String message =
        rejectionMessage(
            """
            [{"key":"cryopreserve","inputs":[{"key":"cryomedium","type":"text"}],
              "effect":{"textFields":[{"nameKey":"a.key","contentFrom":"typo"}]}}]
            """);
    assertTrue(message.contains("takes content from 'typo'"), message);
  }

  @Test
  void acceptsAContentReferenceToAComputedValueRatherThanAnInput() {
    // Passage and Destroy both do this: the wizard DERIVES the value instead of asking for it, so
    // the field's contentFrom names a computed slot, not a declared input.
    assertDoesNotThrow(
        () ->
            new InventoryOperationConfigRegistry(
                new ByteArrayResource(
                    """
                    [{"key":"passage","inputs":[{"key":"sampleName","type":"text"}],
                      "effect":{"nameFrom":"sampleName",
                        "computed":[{"fn":"increment","into":"passageNumber"}],
                        "textFields":[{"nameKey":"a.key","contentFrom":"passageNumber"}]}}]
                    """
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))));
  }

  @Test
  void rejectsAnEffectSourceThatIsNotADeclaredInput() {
    String message =
        rejectionMessage(
            """
            [{"key":"aliquot","inputs":[{"key":"sampleName","type":"text"}],
              "effect":{"nameFrom":"sampleName","countFrom":"count"}}]
            """);
    assertTrue(message.contains("countFrom names 'count'"), message);
  }

  @Test
  void rejectsAnUnknownComputedFunction() {
    // An unknown function makes the validator's content check a no-op, so the field would be
    // accepted carrying anything at all.
    String message =
        rejectionMessage(
            """
            [{"key":"passage","inputs":[],
              "effect":{"computed":[{"fn":"guess","into":"passageNumber"}],
                "textFields":[{"nameKey":"a.key","contentFrom":"passageNumber"}]}}]
            """);
    assertTrue(message.contains("computed function 'guess' is unknown"), message);
  }

  @Test
  void rejectsAComputedValueThatShadowsAnInput() {
    String message =
        rejectionMessage(
            """
            [{"key":"passage","inputs":[{"key":"passageNumber","type":"integer"}],
              "effect":{"computed":[{"fn":"increment","into":"passageNumber"}]}}]
            """);
    assertTrue(message.contains("which is also an input"), message);
  }

  @Test
  void rejectsDuplicateAndBlankInputKeys() {
    assertTrue(
        rejectionMessage(
                """
                [{"key":"aliquot","inputs":[{"key":"count","type":"integer"},
                  {"key":"count","type":"integer"}]}]
                """)
            .contains("duplicate input key 'count'"));
    assertTrue(
        rejectionMessage(
                """
                [{"key":"aliquot","inputs":[{"key":"","type":"integer"}]}]
                """)
            .contains("blank key"));
  }

  @Test
  void rejectsATemperatureRangeThatIsInverted() {
    String message =
        rejectionMessage(
            """
            [{"key":"revive","inputs":[{"key":"storageTemp","type":"temperature",
              "minCelsius":120,"maxCelsius":4}]}]
            """);
    assertTrue(message.contains("minCelsius 120 above maxCelsius 4"), message);
  }

  @Test
  void rejectsAnEmptyingOperationThatAlsoAsksForAnAmount() {
    // The two contradict: the operation takes the whole origin, so an amount the user chose would
    // be collected and then ignored.
    String message =
        rejectionMessage(
            """
            [{"key":"destroy","inputs":[{"key":"amountTaken","type":"quantity"}],
              "effect":{"emptiesOrigin":true,"amountTakenFrom":"amountTaken"}}]
            """);
    assertTrue(message.contains("must not also declare amountTakenFrom"), message);
  }

  @Test
  void reportsEveryViolationAtOnceRatherThanTheFirst() {
    // One round trip to fix a bad config file, not one per boot.
    String message =
        rejectionMessage(
            """
            [{"key":"broken","inputs":[{"key":"a","type":"coordinates"},{"key":"a","type":"text"}],
              "effect":{"nameFrom":"missing","textFields":[{"nameKey":"","contentFrom":"absent"}]}}]
            """);
    for (String expected :
        List.of(
            "type 'coordinates'",
            "duplicate input key 'a'",
            "nameFrom names 'missing'",
            "blank nameKey",
            "takes content from 'absent'")) {
      assertTrue(message.contains(expected), () -> expected + " missing from: " + message);
    }
  }

  @Test
  void reportsAMissingComputedFunctionAsAViolationRatherThanCrashing() {
    // Set.of(...) throws NullPointerException from contains(null), so an omitted "fn" aborted the
    // whole pass with a bare NPE naming neither the operation nor the problem - the exact failure
    // this validation exists to replace (parallel review, C5).
    String message =
        rejectionMessage(
            """
            [{"key":"passage","inputs":[],
              "effect":{"computed":[{"into":"passageNumber"}],
                "textFields":[{"nameKey":"a.key","contentFrom":"passageNumber"}]}}]
            """);
    assertTrue(message.contains("operation 'passage'"), message);
    assertTrue(message.contains("is unknown"), message);
  }

  @Test
  void rejectsABlankOperationKey() {
    assertTrue(
        rejectionMessage(
                """
                [{"key":"","inputs":[]}]
                """)
            .contains("key is blank"));
  }

  @Test
  void rejectsALinkMissingItsFieldNameKeyOrRelationType() {
    String message =
        rejectionMessage(
            """
            [{"key":"aliquot","inputs":[],
              "effect":{"links":[{"relationType":"","fieldNameKey":""}]}}]
            """);
    assertTrue(message.contains("links[0] has a blank fieldNameKey"), message);
    assertTrue(message.contains("links[0] has a blank relationType"), message);
  }
}
