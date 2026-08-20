package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
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
