package com.researchspace.service.inventory.csvimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.field.InventoryRadioField;
import com.researchspace.model.inventory.field.SampleField;
import com.researchspace.model.units.RSUnitDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

public class InventoryImportSampleFieldCreatorTest {

  InventoryImportSampleFieldCreator helper = new InventoryImportSampleFieldCreator();

  @Test
  public void testColumnTypeRecognition() {

    // repeating not too long values -> radio
    List<String> values = new ArrayList<>();
    values.add("yes");
    values.add("no");
    values.add(" ");
    values.add(null);
    values.add("");
    values.add("yes");
    values.add("maybe");
    SampleField field = helper.getSuggestedSampleFieldForNameAndValues("testName", values);
    assertEquals(FieldType.RADIO, field.getType());
    assertEquals(List.of("maybe", "no", "yes"), ((InventoryRadioField) field).getAllOptions());
    assertEquals(3, ((InventoryRadioField) field).getAllOptions().size());

    // repeating values, but starts to be very low ratio -> string
    values.add("who knows");
    field = helper.getSuggestedSampleFieldForNameAndValues("testName", values);
    assertEquals(FieldType.STRING, field.getType());

    // different not too long values -> string
    values.clear();
    values.add("52");
    values.add("2021-01-14");
    values.add("");
    values.add(StringUtils.repeat("y", InventoryImportSampleFieldCreator.MAX_NON_TEXT_LENGTH));
    field = helper.getSuggestedSampleFieldForNameAndValues("testName", values);
    assertEquals(FieldType.STRING, field.getType());

    // numeric values
    values.clear();
    values.add("50");
    values.add("");
    values.add("-15.5");
    values.add("50");
    field = helper.getSuggestedSampleFieldForNameAndValues("testName", values);
    assertEquals(FieldType.NUMBER, field.getType());

    // date values
    values.clear();
    values.add("2021-01-14");
    values.add("");
    values.add("2021-12-31");
    values.add("2021-01-14");
    field = helper.getSuggestedSampleFieldForNameAndValues("testName", values);
    assertEquals(FieldType.DATE, field.getType());

    // time values
    values.clear();
    values.add("08:24");
    values.add("");
    values.add("00:00");
    values.add("08:24");
    field = helper.getSuggestedSampleFieldForNameAndValues("testName", values);
    assertEquals(FieldType.TIME, field.getType());

    // url values
    values.clear();
    values.add("https://researchspace.com");
    values.add("");
    values.add("ftp://howler");
    values.add("https://researchspace.com");
    field = helper.getSuggestedSampleFieldForNameAndValues("testName", values);
    assertEquals(FieldType.URI, field.getType());

    // very long values -> text
    values.clear();
    values.add("x");
    values.add(StringUtils.repeat("y", InventoryImportSampleFieldCreator.MAX_NON_TEXT_LENGTH + 1));
    field = helper.getSuggestedSampleFieldForNameAndValues("testName", values);
    assertEquals(FieldType.TEXT, field.getType());

    // all empty values
    values.clear();
    values.add("");
    values.add(" ");
    values.add(null);
    field = helper.getSuggestedSampleFieldForNameAndValues("testName", values);
    assertEquals(FieldType.STRING, field.getType());
  }

  @Test
  public void testRadioColumnParsing() {

    // the radio options that can be handled fine by front-end and back-end
    List<String> values = List.of("A", "A", "B");
    SampleField field = helper.getSuggestedSampleFieldForNameAndValues("Position (A,B,C)", values);
    assertEquals(FieldType.RADIO, field.getType());
    assertEquals(List.of("A", "B"), ((InventoryRadioField) field).getAllOptions());
    assertEquals(2, ((InventoryRadioField) field).getAllOptions().size());

    // adding ampersand or equals sign messes the formatting (RSINV-150 is raised that'd solve that)
    values = List.of("A&B", "A&B", "B");
    field = helper.getSuggestedSampleFieldForNameAndValues("Position (A,B,C)", values);
    assertEquals(FieldType.STRING, field.getType());
    values = List.of("A=B", "A=B", "B");
    field = helper.getSuggestedSampleFieldForNameAndValues("Position (A,B,C)", values);
    assertEquals(FieldType.STRING, field.getType());
    values = List.of("A", "A", "B");
    field = helper.getSuggestedSampleFieldForNameAndValues("Position (A,B&C)", values);
    assertEquals(FieldType.STRING, field.getType());
    field = helper.getSuggestedSampleFieldForNameAndValues("Position (A,B=C)", values);
    assertEquals(FieldType.STRING, field.getType());
    // sanity check
    field = helper.getSuggestedSampleFieldForNameAndValues("Position (A,B,C)", values);
    assertEquals(FieldType.RADIO, field.getType());
  }

  @Test
  public void testQuantityTypeRecognition() {
    // parseable values
    List<String> values = List.of("1 ml", "2 l", "3 mm³");
    assertEquals(RSUnitDef.MILLI_LITRE, helper.getCommonQuantityUnit(values));
    values = List.of("1 l", "2 ml", "5.2µl", "4cm³");
    assertEquals(RSUnitDef.LITRE, helper.getCommonQuantityUnit(values));
    values = List.of("200g", "12g");
    assertEquals(RSUnitDef.GRAM, helper.getCommonQuantityUnit(values));
    values = List.of("200 g", "1 mg", "5.15μg", "2kg");
    assertEquals(RSUnitDef.GRAM, helper.getCommonQuantityUnit(values));
    values = List.of("200", "1.15");
    assertEquals(RSUnitDef.DIMENSIONLESS, helper.getCommonQuantityUnit(values));

    // unparseable values
    values = List.of("200 g", "1 l"); // different categories
    assertNull(helper.getCommonQuantityUnit(values));
    values = List.of("200", "1 l"); // different categories
    assertNull(helper.getCommonQuantityUnit(values));
    values = List.of("200 g", "5 lb"); // lb not recognized
    assertNull(helper.getCommonQuantityUnit(values));
    values = List.of("200g", "dummy"); // wrong syntax
    assertNull(helper.getCommonQuantityUnit(values));
  }

  @Test
  public void testFieldMappingsForIdentifier() {
    List<String> values = new ArrayList<>();
    values.add("doi.org/10.12345/asdf-fdsa1");
    values.add("https://doi.org/10.12345/asdf-fdsa2");
    values.add("10.12345/asdf-fdsa3");
    values.add("10.1234/asdf-fdsa4");
    values.add("");
    values.add(" ");
    values.add(null);
    Map<String, String> fieldMappings = helper.getFieldMappingForIdentifier("testIgsn", values);
    assertEquals("identifier", fieldMappings.get("testIgsn"));

    // very long values -> text
    values.clear();
    values.add("doi.org/10.12345/asdf-fdsa1");
    values.add("https://doi.org/10.12345/asdf-fdsa2");
    values.add("10.12345/asdf-fdsa3");
    values.add("10.1234/asdf-fdsa4");
    values.add("");
    values.add("NOT_AN_IDENTIFIER");
    values.add(null);
    fieldMappings = helper.getFieldMappingForIdentifier("testIgsn", values);
    assertNull(fieldMappings.get("testIgsn"));

  // all empty values
    values.clear();
    values.add("");
    values.add("");
    values.add("");
    values.add("");
    values.add("");
    values.add(null);
    fieldMappings = helper.getFieldMappingForIdentifier("testIgsn", values);
    assertTrue(fieldMappings.isEmpty());
  }
}
