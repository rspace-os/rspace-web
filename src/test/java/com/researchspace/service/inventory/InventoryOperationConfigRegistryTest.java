package com.researchspace.service.inventory;

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

    InventoryOperationConfig revive = registry.get("revive").orElseThrow();
    InventoryOperationConfig.Input reviveTemp =
        revive.inputs().stream()
            .filter(input -> "temperature".equals(input.type()))
            .findFirst()
            .orElseThrow();
    assertEquals(new BigDecimal("4"), reviveTemp.minCelsius());
    assertEquals(new BigDecimal("120"), reviveTemp.maxCelsius());

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
    assertEquals(
        List.of(new InventoryOperationConfig.Computed("increment", "passageNumber")),
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
}
