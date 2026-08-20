package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.b2inst.model.metadata.B2instInstrumentMetadata;
import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.field.InventoryDateField;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.inventory.field.InventoryLinkField;
import com.researchspace.model.inventory.field.InventoryStringField;
import com.researchspace.model.inventory.field.InventoryTextField;
import com.researchspace.model.inventory.field.InventoryUriField;
import com.researchspace.properties.IPropertyHolder;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the mapping of an Instrument's PIDINST-mapped fields (see CONTEXT.md) into the B2INST
 * draft-record metadata. Uses real {@link Instrument} and field objects rather than mocks, because
 * the mapping reads {@link Instrument#getActiveFields()}, which mocks cannot exercise.
 */
class RspaceToExternalProviderAdapterImplTest {

  private static final String SERVER = "https://rspace.example.com";
  private static final String PUBLIC_PAGE = SERVER + "/public/inventory/abc123XYZ_-456789";

  private IPropertyHolder properties;
  private RspaceToExternalProviderAdapterImpl adapter;

  @BeforeEach
  void setUp() {
    properties = mock(IPropertyHolder.class);
    when(properties.getServerUrl()).thenReturn(SERVER);
    adapter = new RspaceToExternalProviderAdapterImpl(properties);
  }

  private Instrument templateShapedInstrument() {
    User owner = new User("jane");
    owner.setFirstName("Jane");
    owner.setLastName("Doe");
    owner.setEmail("jane@example.org");

    Instrument instrument = new Instrument();
    instrument.setId(5L);
    instrument.setName("Microscope X");
    instrument.setOwner(owner);
    return instrument;
  }

  private void addField(Instrument instrument, InventoryEntityField field) {
    field.setInventoryRecord(instrument);
    // columnIndex must be set before adding: refreshActiveFieldsAndColumnIndex() sorts via
    // InventoryEntityField.compareTo, which NPEs on the null columnIndex that hand-built
    // (non-template-copied) fields start with.
    field.setColumnIndex(instrument.getFields().size() + 1);
    instrument.getFields().add(field);
    instrument.refreshActiveFieldsAndColumnIndex();
  }

  private InventoryStringField stringField(String name, String data) {
    InventoryStringField field = new InventoryStringField(name);
    field.setFieldData(data);
    return field;
  }

  private InventoryDateField dateField(String name, String data) {
    InventoryDateField field = new InventoryDateField(name);
    field.setFieldData(data);
    return field;
  }

  private InventoryUriField uriField(String name, String data) {
    InventoryUriField field = new InventoryUriField(name);
    field.setFieldData(data);
    return field;
  }

  private InventoryLinkField linkField(String name, String relationType, String targetGlobalId) {
    InventoryLinkField field = new InventoryLinkField();
    field.setName(name);
    InventoryLink link = new InventoryLink();
    link.setTargetGlobalId(targetGlobalId);
    link.setRelationType(relationType);
    field.setLink(link);
    return field;
  }

  @Test
  void mapsAllTemplateFieldsIntoMetadata() {
    Instrument instrument = templateShapedInstrument();
    instrument.setDescription("An automatic weather station.");
    addField(instrument, stringField("Owner", "Arctic Research Institute"));
    addField(instrument, stringField("Manufacturer", "Acme Instruments"));
    addField(instrument, stringField("Model", "AWS-42"));
    addField(instrument, stringField("Instrument type", "Weather station"));
    addField(instrument, dateField("Commissioned", "2024-02-21"));
    addField(instrument, dateField("Decommissioned", "2025-04-23"));
    addField(instrument, linkField("Measurement technique", "IsDocumentedBy", "SD101"));
    addField(instrument, stringField("Measured quantity", "Air temperature"));
    addField(instrument, linkField("Calibration", "IsCalibratedBy", "SD202"));
    addField(instrument, dateField("Last calibrated", "2026-01-15"));
    addField(instrument, uriField("Landing page", "https://lab.example.org/aws-42"));
    addField(instrument, stringField("Alternate Identifier", "INV-2025-0042"));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, null).getMetadata();

    assertEquals("Microscope X", md.getName());
    assertEquals("1.0", md.getSchemaVersion());
    assertEquals("An automatic weather station.", md.getDescription());
    assertEquals(1, md.getOwner().size());
    assertEquals("Arctic Research Institute", md.getOwner().get(0).getOwnerName());
    assertEquals("jane@example.org", md.getOwner().get(0).getOwnerContact());
    assertEquals("Acme Instruments", md.getManufacturer().get(0).getManufacturerName());
    assertEquals("AWS-42", md.getModel().getModelName());
    assertEquals("Weather station", md.getInstrumentType().get(0).getInstrumentTypeName());
    assertEquals(2, md.getDate().size());
    assertEquals("2024-02-21", md.getDate().get(0).getDate());
    assertEquals("Commissioned", md.getDate().get(0).getDateType());
    assertEquals("2025-04-23", md.getDate().get(1).getDate());
    assertEquals("DeCommissioned", md.getDate().get(1).getDateType());
    // only the measured quantity reaches MeasuredVariable, as its plain content
    assertEquals(List.of("Air temperature"), md.getMeasuredVariable());
    assertEquals("https://lab.example.org/aws-42", md.getLandingPage());
    assertEquals("Other", md.getAlternateIdentifier().get(0).getAlternateIdentifierType());
    assertEquals("INV-2025-0042", md.getAlternateIdentifier().get(0).getAlternateIdentifierValue());
  }

  @Test
  void ownerFallsBackToRecordOwnerWhenFieldMissingOrBlank() {
    Instrument instrument = templateShapedInstrument();
    addField(instrument, stringField("Owner", "   "));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, null).getMetadata();

    assertEquals("Jane Doe", md.getOwner().get(0).getOwnerName());
    assertEquals("jane@example.org", md.getOwner().get(0).getOwnerContact());
  }

  @Test
  void landingPageFallsBackToThePublicLandingPage() {
    Instrument instrument = templateShapedInstrument();

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, PUBLIC_PAGE).getMetadata();

    assertEquals(PUBLIC_PAGE, md.getLandingPage());
  }

  /**
   * Slash handling on the server URL now lives in {@code ApiInventoryDOI.getPublicLandingPageUrl}
   * (covered by ApiInventoryDOITest); here the adapter must pass a provided URL through untouched.
   */
  @Test
  void providedPublicLandingPageIsRegisteredVerbatim() {
    Instrument instrument = templateShapedInstrument();

    B2instInstrumentMetadata md =
        adapter
            .buildB2instDoi(instrument, "https://other.example.org/public/inventory/x")
            .getMetadata();

    assertEquals("https://other.example.org/public/inventory/x", md.getLandingPage());
  }

  /** The materialised globalId default is not a user's landing page and never gets registered. */
  @Test
  void materialisedDefaultLandingPageIsSupersededByThePublicLandingPage() {
    Instrument instrument = templateShapedInstrument();
    addField(
        instrument,
        uriField("Landing page", SERVER + "/globalId/" + instrument.getGlobalIdentifier()));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, PUBLIC_PAGE).getMetadata();

    assertEquals(PUBLIC_PAGE, md.getLandingPage());
  }

  @Test
  void userTypedLandingPageWinsOverThePublicLandingPage() {
    Instrument instrument = templateShapedInstrument();
    addField(instrument, uriField("Landing page", "https://lab.example.org/aws-42"));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, PUBLIC_PAGE).getMetadata();

    assertEquals("https://lab.example.org/aws-42", md.getLandingPage());
  }

  /**
   * A default-valued field with no public URL available: omitted. Registering the login-walled
   * default would bake a wrong URL into a citable PID; a missing property is recoverable.
   */
  @Test
  void landingPageIsOmittedWhenOnlyTheDefaultAndNoPublicUrlExist() {
    Instrument instrument = templateShapedInstrument();
    addField(
        instrument,
        uriField("Landing page", SERVER + "/globalId/" + instrument.getGlobalIdentifier()));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, null).getMetadata();

    assertNull(md.getLandingPage());
  }

  /**
   * The default is recognised by its {@code /globalId/<globalId>} tail, not only by equality with
   * the currently configured address. A deployment that has since been renamed, or that has lost
   * its server URL setting, must not start registering the login-walled default it filled in
   * earlier: that is the exact outcome ADR 0006 exists to prevent, and it cannot be undone once a
   * curator accepts the record.
   */
  @Test
  void materialisedDefaultIsRecognisedAfterTheServerUrlChanged() {
    when(properties.getServerUrl()).thenReturn(null);
    Instrument instrument = templateShapedInstrument();
    addField(
        instrument,
        uriField(
            "Landing page",
            "https://old-name.example.com/globalId/" + instrument.getGlobalIdentifier()));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, PUBLIC_PAGE).getMetadata();

    assertEquals(PUBLIC_PAGE, md.getLandingPage());
  }

  /** The same recognition with nothing to fall back to: omitted, never the login-walled default. */
  @Test
  void materialisedDefaultFromAnOldServerUrlIsOmittedWhenNoPublicUrlExists() {
    when(properties.getServerUrl()).thenReturn("https://new-name.example.com");
    Instrument instrument = templateShapedInstrument();
    addField(
        instrument,
        uriField(
            "Landing page",
            "https://old-name.example.com/globalId/" + instrument.getGlobalIdentifier()));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, null).getMetadata();

    assertNull(md.getLandingPage());
  }

  /**
   * With no server URL configured there is no correct address to send, and a LandingPage is baked
   * into a citable PID the moment a curator accepts the record, with no way for RSpace to update a
   * published B2INST record afterwards. So the property is omitted rather than sent site-relative:
   * absent is recoverable, wrong-and-published is not.
   */
  @Test
  void landingPageIsOmittedRatherThanRelativeWhenServerUrlIsUnset() {
    when(properties.getServerUrl()).thenReturn(null);
    Instrument instrument = templateShapedInstrument();

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, null).getMetadata();

    assertNull(md.getLandingPage());
  }

  /** A Landing page the user typed is unaffected by an unconfigured server URL. */
  @Test
  void landingPageFromTheFieldSurvivesAnUnsetServerUrl() {
    when(properties.getServerUrl()).thenReturn(null);
    Instrument instrument = templateShapedInstrument();
    addField(instrument, uriField("Landing page", "https://lab.example.org/aws-42"));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, null).getMetadata();

    assertEquals("https://lab.example.org/aws-42", md.getLandingPage());
  }

  @Test
  void skipsFieldsWithWrongType() {
    Instrument instrument = templateShapedInstrument();
    InventoryTextField bogusDate = new InventoryTextField("Commissioned");
    bogusDate.setFieldData("around 2019, ask Bob");
    addField(instrument, bogusDate);
    InventoryTextField bogusLanding = new InventoryTextField("Landing page");
    bogusLanding.setFieldData("my lab bench");
    addField(instrument, bogusLanding);

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, PUBLIC_PAGE).getMetadata();

    assertNull(md.getDate());
    assertEquals(PUBLIC_PAGE, md.getLandingPage());
  }

  @Test
  void matchesFieldNamesCaseInsensitivelyAndTrimmed() {
    Instrument instrument = templateShapedInstrument();
    addField(instrument, stringField("  MANUFACTURER  ", "Acme Instruments"));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, null).getMetadata();

    assertEquals("Acme Instruments", md.getManufacturer().get(0).getManufacturerName());
  }

  @Test
  void doesNotMapMeasurementTechniqueCalibrationOrLastCalibrated() {
    Instrument instrument = templateShapedInstrument();
    addField(instrument, linkField("Measurement technique", "IsDocumentedBy", "SD101"));
    addField(instrument, linkField("Calibration", "IsCalibratedBy", "SD202"));
    addField(instrument, dateField("Last calibrated", "2026-01-15"));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, null).getMetadata();

    // fully populated, and still nothing: these three fields feed no PIDINST property
    assertNull(md.getMeasuredVariable());
  }

  @Test
  void mapsMeasuredQuantityAsPlainContent() {
    Instrument instrument = templateShapedInstrument();
    addField(instrument, stringField("Measured quantity", "  Air temperature  "));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, null).getMetadata();

    assertEquals(List.of("Air temperature"), md.getMeasuredVariable());
  }

  @Test
  void omitsMeasuredVariableWhenMeasuredQuantityIsBlank() {
    Instrument instrument = templateShapedInstrument();
    addField(instrument, stringField("Measured quantity", "   "));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, null).getMetadata();

    assertNull(md.getMeasuredVariable());
  }

  @Test
  void nonTemplateInstrumentKeepsBaselinePayloadPlusLandingPage() {
    Instrument instrument = templateShapedInstrument();

    B2instDoi doi = adapter.buildB2instDoi(instrument, PUBLIC_PAGE);
    B2instInstrumentMetadata md = doi.getMetadata();

    assertEquals("Microscope X", md.getName());
    assertEquals("1.0", md.getSchemaVersion());
    assertEquals("Jane Doe", md.getOwner().get(0).getOwnerName());
    assertNull(md.getDescription());
    assertNull(md.getManufacturer());
    assertNull(md.getModel());
    assertNull(md.getInstrumentType());
    assertNull(md.getDate());
    assertNull(md.getMeasuredVariable());
    assertNull(md.getAlternateIdentifier());
    assertEquals(PUBLIC_PAGE, md.getLandingPage());
    assertEquals("public", doi.getAccess().getRecord());
    assertFalse(doi.getFiles().getEnabled());
  }

  @Test
  void wireFormatUsesPidinstKeys() throws Exception {
    Instrument instrument = templateShapedInstrument();
    addField(instrument, dateField("Commissioned", "2024-02-21"));
    addField(instrument, stringField("Alternate Identifier", "INV-1"));

    JsonNode root = new ObjectMapper().valueToTree(adapter.buildB2instDoi(instrument, null));

    assertEquals("Microscope X", root.at("/metadata/Name").asText());
    assertEquals("1.0", root.at("/metadata/SchemaVersion").asText());
    assertEquals("2024-02-21", root.at("/metadata/Date/0/Date").asText());
    assertEquals("Commissioned", root.at("/metadata/Date/0/dateType").asText());
    assertEquals(
        "Other", root.at("/metadata/AlternateIdentifier/0/alternateIdentifierType").asText());
    assertEquals("public", root.at("/access/record").asText());
    assertFalse(root.at("/files/enabled").asBoolean());
  }

  /**
   * Pins the outbound key of every property this adapter can emit. Every other test in this class
   * asserts through getters, which bypasses Jackson entirely, so a renamed {@code @JsonProperty} in
   * the pinned rspace-core-model would leave them all green while B2INST silently dropped the
   * property.
   */
  @Test
  void wireFormatNamesEveryEmittedProperty() throws Exception {
    Instrument instrument = templateShapedInstrument();
    instrument.setDescription("An automatic weather station.");
    addField(instrument, stringField("Owner", "Arctic Research Institute"));
    addField(instrument, stringField("Manufacturer", "Acme Instruments"));
    addField(instrument, stringField("Model", "AWS-42"));
    addField(instrument, stringField("Instrument type", "Weather station"));
    addField(instrument, stringField("Measured quantity", "Air temperature"));
    addField(instrument, uriField("Landing page", "https://lab.example.org/aws-42"));

    JsonNode md =
        new ObjectMapper().valueToTree(adapter.buildB2instDoi(instrument, null)).at("/metadata");

    assertEquals("An automatic weather station.", md.at("/Description").asText());
    assertEquals("Arctic Research Institute", md.at("/Owner/0/ownerName").asText());
    assertEquals("jane@example.org", md.at("/Owner/0/ownerContact").asText());
    assertEquals("Acme Instruments", md.at("/Manufacturer/0/manufacturerName").asText());
    assertEquals("AWS-42", md.at("/Model/modelName").asText());
    assertEquals("Weather station", md.at("/InstrumentType/0/instrumentTypeName").asText());
    assertEquals("Air temperature", md.at("/MeasuredVariable/0").asText());
    assertEquals("https://lab.example.org/aws-42", md.at("/LandingPage").asText());
  }

  /**
   * {@code nullIfEmpty} exists only so an absent property is omitted rather than sent as null or as
   * an empty list, and that is invisible to a getter-based assertion. Assert the omission on the
   * wire, or the one behaviour that method was written for is untested.
   */
  @Test
  void wireFormatOmitsPropertiesWithNoContentRatherThanSendingThemEmpty() throws Exception {
    JsonNode md =
        new ObjectMapper()
            .valueToTree(adapter.buildB2instDoi(templateShapedInstrument(), PUBLIC_PAGE))
            .at("/metadata");

    assertTrue(md.at("/Description").isMissingNode());
    assertTrue(md.at("/Manufacturer").isMissingNode());
    assertTrue(md.at("/Model").isMissingNode());
    assertTrue(md.at("/InstrumentType").isMissingNode());
    assertTrue(md.at("/Date").isMissingNode());
    assertTrue(md.at("/MeasuredVariable").isMissingNode());
    assertTrue(md.at("/AlternateIdentifier").isMissingNode());
    // Name, SchemaVersion and Owner always resolve, and LandingPage does here because a public
    // landing page was supplied, so all four must still be present
    assertFalse(md.at("/Name").isMissingNode());
    assertFalse(md.at("/SchemaVersion").isMissingNode());
    assertFalse(md.at("/Owner").isMissingNode());
    assertFalse(md.at("/LandingPage").isMissingNode());
  }

  @Test
  void rejectsNonInstrumentRecord() {
    InventoryRecord notAnInstrument = mock(InventoryRecord.class);
    when(notAnInstrument.isInstrument()).thenReturn(false);

    assertThrows(
        IllegalArgumentException.class, () -> adapter.buildB2instDoi(notAnInstrument, null));
  }

  @Test
  void delegatesDataCiteToConvertToDataCiteDoi() {
    ApiInventoryDOI doi = new ApiInventoryDOI();
    doi.setTitle("My DOI");

    DataCiteDoi result = adapter.buildDataCiteDoi(doi);

    assertEquals("My DOI", result.getAttributes().getTitles().get(0).getTitle());
  }
}
