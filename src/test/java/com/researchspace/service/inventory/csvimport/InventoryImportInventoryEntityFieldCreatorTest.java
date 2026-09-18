package com.researchspace.service.inventory.csvimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryRadioField;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.inventory.csvexport.InventoryItemCsvExporter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class InventoryImportInventoryEntityFieldCreatorTest {

  InventoryImportSampleFieldCreator helper = new InventoryImportSampleFieldCreator();

  @BeforeEach
  void wireLinkParser() {
    CsvLinkValueParser linkParser = new CsvLinkValueParser();
    IPropertyHolder properties = mock(IPropertyHolder.class);
    when(properties.getServerUrl()).thenReturn("https://rspace.example.com");
    linkParser.properties = properties;
    helper.linkParser = linkParser;
  }

  @Test
  public void linkSuggestedOnlyWhenEveryValueIsALinkCell() {
    List<String> values = new ArrayList<>();
    values.add("IsDerivedFrom https://rspace.example.com/globalId/SA1v2");
    values.add("");
    values.add("Cites https://rspace.example.com/globalId/SD9");
    InventoryEntityField field = helper.getSuggestedSampleFieldForNameAndValues("links", values);
    assertEquals(FieldType.LINK, field.getType());
    assertEquals("links", field.getName());

    values.add("Cites https://elsewhere.example.com/globalId/SD9");
    field = helper.getSuggestedSampleFieldForNameAndValues("links", values);
    assertEquals(FieldType.STRING, field.getType());
  }

  @Test
  public void exportSentinelDoesNotStopAColumnBeingSuggestedAsLink() {
    // a multi-record export fills columns a row does not have with "#N/A", so an extra-field or
    // per-template link column carries the sentinel on every unrelated row. Treating it as real
    // data infers STRING and the round trip the feature promises never gets off the ground.
    List<String> values = new ArrayList<>();
    values.add("IsDerivedFrom https://rspace.example.com/globalId/SA1v2");
    values.add(InventoryItemCsvExporter.CSV_VALUE_UNAVAILABLE_ITEM_PROPERTY);
    values.add("Cites https://rspace.example.com/globalId/SD9");

    assertEquals(
        FieldType.LINK, helper.getSuggestedSampleFieldForNameAndValues("links", values).getType());
  }

  @Test
  public void aColumnOfNothingButExportSentinelsIsNotALinkColumn() {
    // filtering the sentinel out must not leave an empty set voting yes by default: a column no
    // exported record actually filled in says nothing about links
    List<String> values = new ArrayList<>();
    values.add(InventoryItemCsvExporter.CSV_VALUE_UNAVAILABLE_ITEM_PROPERTY);
    values.add(InventoryItemCsvExporter.CSV_VALUE_UNAVAILABLE_ITEM_PROPERTY);

    assertEquals(
        FieldType.STRING, helper.getSuggestedSampleFieldForNameAndValues("n", values).getType());
  }

  @Test
  public void urlBearingValueIsNonTextUpToExactlyTheRaisedLimit() {
    // the boundary itself: an off-by-one in the length guard silently stops suggesting Link
    // for a cell on a long hostname, and the wide 150-vs-576 cases above would not notice
    String url = "https://x.example.com/y";
    // text first, as in longValueContainingUrlIsNotForcedToText: a value that IS a bare URI is
    // suggested as URI, which is a different branch from the length guard under test here
    String prefix = "see " + url + " ";
    String atLimit = prefix + StringUtils.repeat("y", 500 - prefix.length());
    assertEquals(500, atLimit.length());
    assertEquals(
        FieldType.STRING,
        helper.getSuggestedSampleFieldForNameAndValues("n", List.of(atLimit)).getType());

    String overLimit = atLimit + "y";
    assertEquals(501, overLimit.length());
    assertEquals(
        FieldType.TEXT,
        helper.getSuggestedSampleFieldForNameAndValues("n", List.of(overLimit)).getType());
  }

  @Test
  public void longValueContainingUrlIsNotForcedToText() {
    String url = "https://rsdev-1354-export-import-inventory-links-bb57ac5e-3.researchspace.com/x";
    String longWithUrl = "see " + url + " " + StringUtils.repeat("y", 150 - url.length() - 5);
    assertTrue(longWithUrl.length() > InventoryImportSampleFieldCreator.MAX_NON_TEXT_LENGTH);
    InventoryEntityField field =
        helper.getSuggestedSampleFieldForNameAndValues("n", List.of(longWithUrl));
    assertEquals(FieldType.STRING, field.getType());

    String longWithoutUrl = StringUtils.repeat("y", 150);
    field = helper.getSuggestedSampleFieldForNameAndValues("n", List.of(longWithoutUrl));
    assertEquals(FieldType.TEXT, field.getType());

    String tooLongEvenWithUrl = url + " " + StringUtils.repeat("y", 500);
    field = helper.getSuggestedSampleFieldForNameAndValues("n", List.of(tooLongEvenWithUrl));
    assertEquals(FieldType.TEXT, field.getType());

    // a link cell on a server with a long hostname stays eligible for the Link type
    String longLinkCell =
        "IsVersionOf https://rsdev-1354-export-import-inventory-links-bb57ac5e-3.researchspace.com"
            + "/globalId/SA1711111";
    helper.linkParser.properties = mock(IPropertyHolder.class);
    when(helper.linkParser.properties.getServerUrl())
        .thenReturn(
            "https://rsdev-1354-export-import-inventory-links-bb57ac5e-3.researchspace.com");
    field = helper.getSuggestedSampleFieldForNameAndValues("n", List.of(longLinkCell));
    assertEquals(FieldType.LINK, field.getType());
  }

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
    InventoryEntityField field = helper.getSuggestedSampleFieldForNameAndValues("testName", values);
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
    InventoryEntityField field =
        helper.getSuggestedSampleFieldForNameAndValues("Position (A,B,C)", values);
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
