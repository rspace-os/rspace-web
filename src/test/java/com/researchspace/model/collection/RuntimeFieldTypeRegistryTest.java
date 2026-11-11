package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.collection.RuntimeFieldTypeRegistry.Registration;
import com.researchspace.model.collection.types.runtime.RuntimeChoiceType;
import com.researchspace.model.collection.types.runtime.RuntimeDateType;
import com.researchspace.model.collection.types.runtime.RuntimeNumberType;
import com.researchspace.model.collection.types.runtime.RuntimeRadioType;
import com.researchspace.model.collection.types.runtime.RuntimeTextType;
import com.researchspace.model.collection.types.runtime.RuntimeTimeType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeFieldTypeRegistryTest {
  @Test
  void explicitlyRegistersEveryBuiltinImplementation() {
    RuntimeFieldTypeRegistry registry = RuntimeFieldTypeRegistry.builtIns();

    assertInstanceOf(RuntimeTextType.class, registry.require(RuntimeFieldValueType.TEXT));
    assertInstanceOf(RuntimeNumberType.class, registry.require(RuntimeFieldValueType.NUMBER));
    assertInstanceOf(RuntimeDateType.class, registry.require(RuntimeFieldValueType.DATE));
    assertInstanceOf(RuntimeTimeType.class, registry.require(RuntimeFieldValueType.TIME));
    assertInstanceOf(RuntimeRadioType.class, registry.require(RuntimeFieldValueType.RADIO));
    assertInstanceOf(RuntimeChoiceType.class, registry.require(RuntimeFieldValueType.CHOICE));
    assertEquals(
        FieldComparisonStrategy.NUMERIC_TEXT, RuntimeFieldValueType.NUMBER.comparisonStrategy());
    assertEquals(
        FieldComparisonStrategy.CHOICE_TEXT, RuntimeFieldValueType.CHOICE.comparisonStrategy());
    assertEquals(FieldComparisonStrategy.SCALAR, RuntimeFieldValueType.DATE.comparisonStrategy());
    assertTrue(RuntimeFieldValueType.TEXT.supportsWildcards());
    assertFalse(RuntimeFieldValueType.RADIO.supportsWildcards());
  }

  @Test
  void rejectsDuplicateAndMissingIdentifiers() {
    List<Registration> registrations = new ArrayList<>(builtInRegistrations());
    registrations.add(registrations.get(0));
    IllegalArgumentException duplicate =
        assertThrows(
            IllegalArgumentException.class, () -> new RuntimeFieldTypeRegistry(registrations));
    assertEquals("Duplicate runtime field type: TEXT", duplicate.getMessage());

    registrations.remove(0);
    registrations.remove(registrations.size() - 1);
    IllegalArgumentException missing =
        assertThrows(
            IllegalArgumentException.class, () -> new RuntimeFieldTypeRegistry(registrations));
    assertEquals("Missing runtime field type: TEXT", missing.getMessage());
  }

  @Test
  void snapshotsRegistrationsAndUsesTheRegisteredImplementation() {
    List<Registration> registrations = new ArrayList<>(builtInRegistrations());
    RuntimeFieldType replacement = new RuntimeRadioType();
    registrations.set(0, new Registration(RuntimeFieldValueType.TEXT, replacement));

    RuntimeFieldTypeRegistry registry = new RuntimeFieldTypeRegistry(registrations);
    registrations.clear();

    assertSame(replacement, registry.require(RuntimeFieldValueType.TEXT));
    assertFalse(registry.require(RuntimeFieldValueType.TEXT).supportsWildcards());
    assertTrue(
        RuntimeFieldTypeRegistry.builtIns()
            .require(RuntimeFieldValueType.TEXT)
            .supportsWildcards());
    assertEquals("string", registry.require(RuntimeFieldValueType.TIME).jsonType());
  }

  private List<Registration> builtInRegistrations() {
    return Arrays.stream(RuntimeFieldValueType.values())
        .map(
            identifier ->
                new Registration(
                    identifier, RuntimeFieldTypeRegistry.builtIns().require(identifier)))
        .toList();
  }
}
