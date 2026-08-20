package com.researchspace.model.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.inventory.field.ExtraTextField;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InventoryRecordReservedFieldNamesTest {

  private static final Set<String> BASE_RESERVED =
      Set.of("name", "description", "tags", "preview image", "attachments");

  @Test
  void containerReservedFieldNames() {
    Container container = new Container(Container.ContainerType.LIST);
    assertEquals(
        union(BASE_RESERVED, "can store", "type", "locations image", "grid dimensions"),
        container.getReservedFieldNames());
  }

  @Test
  void subSampleReservedFieldNames() {
    SubSample subSample = new SubSample();
    assertEquals(
        union(BASE_RESERVED, "quantity", "sample", "notes"),
        subSample.getReservedFieldNames());
  }

  @Test
  void sampleReservedFieldNames() {
    Sample sample = new Sample();
    assertEquals(
        union(
            BASE_RESERVED,
            "source",
            "expiry date",
            "sample template",
            "storage temperature",
            "total quantity",
            "subsamples"),
        sample.getReservedFieldNames());
  }

  @Test
  void sampleTemplateReservedFieldNames() {
    SampleTemplate template = new SampleTemplate();
    assertEquals(
        union(
            BASE_RESERVED,
            "source",
            "expiry date",
            "subsample alias",
            "quantity units",
            "fields",
            "samples"),
        template.getReservedFieldNames());
  }

  @Test
  void instrumentReservedFieldNames() {
    Instrument instrument = new Instrument();
    assertEquals(BASE_RESERVED, instrument.getReservedFieldNames());
  }

  @Test
  void instrumentTemplateReservedFieldNames() {
    InstrumentTemplate instrumentTemplate = new InstrumentTemplate();
    assertEquals(BASE_RESERVED, instrumentTemplate.getReservedFieldNames());
  }

  @Test
  void allReservedNamesAreStoredLowercase() {
    Set<String> allSets = new HashSet<>();
    allSets.addAll(new Container(Container.ContainerType.LIST).getReservedFieldNames());
    allSets.addAll(new SubSample().getReservedFieldNames());
    allSets.addAll(new Sample().getReservedFieldNames());
    SampleTemplate template = new SampleTemplate();
    allSets.addAll(template.getReservedFieldNames());
    allSets.addAll(new Instrument().getReservedFieldNames());
    allSets.addAll(new InstrumentTemplate().getReservedFieldNames());

    for (String name : allSets) {
      assertEquals(name.toLowerCase(), name,
          "Reserved name '" + name + "' should be stored lowercase");
    }
  }

  @Test
  void containerAddExtraFieldRejectsDisplayedLabel() {
    Container container = new Container(Container.ContainerType.LIST);
    ExtraTextField field = new ExtraTextField();
    field.setName("Type");

    IllegalArgumentException iae =
        assertThrows(IllegalArgumentException.class, () -> container.addExtraField(field));
    assertTrue(
        iae.getMessage().contains("'Type'"),
        "Expected message to mention 'Type', got: " + iae.getMessage());
  }

  @Test
  void subSampleAddExtraFieldRejectsDisplayedLabel() {
    SubSample subSample = new SubSample();
    ExtraTextField field = new ExtraTextField();
    field.setName("Notes");

    assertThrows(IllegalArgumentException.class, () -> subSample.addExtraField(field));
  }

  @Test
  void sampleAddExtraFieldRejectsBaseDisplayedLabel() {
    Sample sample = new Sample();
    ExtraTextField field = new ExtraTextField();
    field.setName("Preview Image");

    assertThrows(IllegalArgumentException.class, () -> sample.addExtraField(field));
  }

  @Test
  void verifyFieldNameAllowedKeepsExistingReservedNameRejection() {
    Container container = new Container(Container.ContainerType.LIST);
    ExtraTextField field = new ExtraTextField();
    field.setName("description");

    assertThrows(IllegalArgumentException.class, () -> container.addExtraField(field));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Type", "type", "TYPE", "tYpE", "tyPE"})
  void containerRejectsTypeInAnyCase(String fieldName) {
    Container container = new Container(Container.ContainerType.LIST);
    ExtraTextField field = new ExtraTextField();
    field.setName(fieldName);

    assertThrows(IllegalArgumentException.class, () -> container.addExtraField(field));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Preview Image", "preview image", "PREVIEW IMAGE", "Preview IMAGE"})
  void sampleRejectsBaseLabelInAnyCase(String fieldName) {
    Sample sample = new Sample();
    ExtraTextField field = new ExtraTextField();
    field.setName(fieldName);

    assertThrows(IllegalArgumentException.class, () -> sample.addExtraField(field));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Name", "NAME", "nAmE", "Description", "DESCRIPTION", "Tags", "TAGS"})
  void containerRejectsLegacyReservedInAnyCase(String fieldName) {
    Container container = new Container(Container.ContainerType.LIST);
    ExtraTextField field = new ExtraTextField();
    field.setName(fieldName);

    assertThrows(IllegalArgumentException.class, () -> container.addExtraField(field));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Subsample Alias", "subsample alias", "SUBSAMPLE ALIAS", "Quantity Units"})
  void sampleTemplateRejectsTemplateLabelInAnyCase(String fieldName) {
    SampleTemplate template = new SampleTemplate();
    ExtraTextField field = new ExtraTextField();
    field.setName(fieldName);

    assertThrows(IllegalArgumentException.class, () -> template.addExtraField(field));
  }

  private static Set<String> union(Set<String> base, String... extras) {
    Set<String> result = new HashSet<>(base);
    for (String extra : extras) {
      result.add(extra);
    }
    return result;
  }
}
