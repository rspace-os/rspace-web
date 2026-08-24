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
import com.researchspace.b2inst.model.metadata.B2instRelatedIdentifier;
import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.datacite.model.DataCiteDoiAttributes;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
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

  private RspaceToExternalProviderAdapterImpl adapter;
  private IPropertyHolder properties;

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

  /** The legacy auto-filled landing page is not a user's landing page and never gets registered. */
  @Test
  void legacyAutoFilledLandingPageIsSupersededByThePublicLandingPage() {
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
   * A hand-typed address with no scheme is not something a resolver can follow, and the field's own
   * validation does not catch it: core-model's InventoryUriField only checks that {@code new URI()}
   * parses, which a bare host or a relative path does. The code refuses to emit a site-relative
   * address of its own, so accepting a typed one would be inconsistent. Fall back to the
   * identifier's public page, which does resolve.
   */
  @Test
  void schemeLessUserTypedLandingPageIsNotRegistered() {
    Instrument instrument = templateShapedInstrument();
    addField(instrument, uriField("Landing page", "lab.example.org/aws-42"));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, PUBLIC_PAGE).getMetadata();

    assertEquals(PUBLIC_PAGE, md.getLandingPage());
  }

  /**
   * Regression pin rather than a driving case: the absolute-http(s) rule above already blocks a
   * non-web scheme. Worth pinning explicitly because {@code new URI("javascript:...")} parses
   * cleanly, so the field's own validation lets it through, and the value would otherwise be
   * published in a third party's record.
   */
  @Test
  void nonWebSchemeUserTypedLandingPageIsNotRegistered() {
    Instrument instrument = templateShapedInstrument();
    addField(instrument, uriField("Landing page", "javascript:alert(1)"));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, PUBLIC_PAGE).getMetadata();

    assertEquals(PUBLIC_PAGE, md.getLandingPage());
  }

  /**
   * The resolvable-address rule has to cover the fallback too, not just the field. The public
   * landing page is built from the deployment's server URL, which is bound by a plain
   * {@code @Value} with no scheme validation, so a deployment configured as {@code
   * rspace.example.com} would otherwise register exactly the scheme-less form we refuse from users.
   */
  @Test
  void schemeLessPublicLandingPageIsNotRegisteredEither() {
    Instrument instrument = templateShapedInstrument();

    B2instInstrumentMetadata md =
        adapter
            .buildB2instDoi(instrument, "rspace.example.com/public/inventory/abc123")
            .getMetadata();

    assertNull(md.getLandingPage());
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
   * A near miss of a legacy auto-filled landing page still names the same login-walled page: a
   * trailing slash resolves to the record's globalId page just as the bare address does. An exact
   * tail match lets a hand-edited default through and registers it, which ADR 0006 forbids and
   * which cannot be undone once a curator accepts.
   */
  @Test
  void legacyAutoFilledLandingPageIsRecognisedDespiteATrailingSlash() {
    Instrument instrument = templateShapedInstrument();
    addField(
        instrument,
        uriField("Landing page", SERVER + "/globalId/" + instrument.getGlobalIdentifier() + "/"));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, PUBLIC_PAGE).getMetadata();

    assertEquals(PUBLIC_PAGE, md.getLandingPage());
  }

  /**
   * A query string does not change which page an address names, so the default carrying one is
   * still the login-walled page and must not be registered.
   */
  @Test
  void legacyAutoFilledLandingPageIsRecognisedDespiteAQueryString() {
    Instrument instrument = templateShapedInstrument();
    addField(
        instrument,
        uriField(
            "Landing page",
            SERVER + "/globalId/" + instrument.getGlobalIdentifier() + "?from=email"));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, PUBLIC_PAGE).getMetadata();

    assertEquals(PUBLIC_PAGE, md.getLandingPage());
  }

  /**
   * Dot segments resolve away, so this is still the login-walled page. The tail is compared against
   * the normalised path rather than the raw text, or a hand-edited default could hide behind them.
   */
  @Test
  void legacyAutoFilledLandingPageIsRecognisedThroughDotSegments() {
    Instrument instrument = templateShapedInstrument();
    String globalId = instrument.getGlobalIdentifier();
    addField(
        instrument, uriField("Landing page", SERVER + "/globalId/" + globalId + "/../" + globalId));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, PUBLIC_PAGE).getMetadata();

    assertEquals(PUBLIC_PAGE, md.getLandingPage());
  }

  /**
   * Regression pins for the other two forms the normalised-path comparison covers: more than one
   * trailing slash (containers collapse them), and a percent-escape of an unreserved character
   * ({@code %31} is {@code 1}). Both resolve to the same login-walled page.
   */
  @Test
  void legacyAutoFilledLandingPageIsRecognisedThroughRedundantSlashesAndPercentEscapes() {
    Instrument doubleSlash = templateShapedInstrument();
    addField(
        doubleSlash,
        uriField("Landing page", SERVER + "/globalId/" + doubleSlash.getGlobalIdentifier() + "//"));
    assertEquals(
        PUBLIC_PAGE,
        adapter.buildB2instDoi(doubleSlash, PUBLIC_PAGE).getMetadata().getLandingPage());

    // IN5 written as IN%35, which decodes back to IN5
    Instrument escaped = templateShapedInstrument();
    addField(escaped, uriField("Landing page", SERVER + "/globalId/IN%35"));
    assertEquals(
        PUBLIC_PAGE, adapter.buildB2instDoi(escaped, PUBLIC_PAGE).getMetadata().getLandingPage());
  }

  /** Nor does a fragment: same page, so still never registered. */
  @Test
  void legacyAutoFilledLandingPageIsRecognisedDespiteAFragment() {
    Instrument instrument = templateShapedInstrument();
    addField(
        instrument,
        uriField(
            "Landing page", SERVER + "/globalId/" + instrument.getGlobalIdentifier() + "#details"));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, PUBLIC_PAGE).getMetadata();

    assertEquals(PUBLIC_PAGE, md.getLandingPage());
  }

  /**
   * Case in the global id is folded too. Such an address either resolves to the same login-walled
   * page or to nothing at all, and neither is fit to bake into a citable PID.
   */
  @Test
  void legacyAutoFilledLandingPageIsRecognisedDespiteGlobalIdCase() {
    Instrument instrument = templateShapedInstrument();
    addField(
        instrument,
        uriField(
            "Landing page",
            SERVER + "/globalId/" + instrument.getGlobalIdentifier().toLowerCase()));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, PUBLIC_PAGE).getMetadata();

    assertEquals(PUBLIC_PAGE, md.getLandingPage());
  }

  /**
   * Recognition is pinned to <em>this</em> record's global id, not to the {@code /globalId/} path
   * alone. A user who links to a different record's page has typed that deliberately, so it is
   * registered. Without this case the guard could be weakened to a bare {@code contains} and the
   * rest of the suite would stay green while silently discarding such links.
   */
  @Test
  void aLinkToAnotherRecordsGlobalIdPageIsTreatedAsUserTyped() {
    Instrument instrument = templateShapedInstrument();
    addField(instrument, uriField("Landing page", SERVER + "/globalId/IN999"));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, PUBLIC_PAGE).getMetadata();

    assertEquals(SERVER + "/globalId/IN999", md.getLandingPage());
  }

  /**
   * Recognition is host agnostic: the {@code /globalId/<globalId>} tail is what the default-fill
   * produces and names this one record, while the host is only whatever the server URL said at fill
   * time. So a default written under an old deployment name is still recognised. That matters
   * because the alternative, comparing whole addresses, would start registering the login-walled
   * default after a rename, which is the exact outcome ADR 0006 exists to prevent and cannot be
   * undone once a curator accepts the record.
   */
  @Test
  void legacyAutoFilledLandingPageIsRecognisedOnAnyHost() {
    Instrument instrument = templateShapedInstrument();
    addField(
        instrument,
        uriField(
            "Landing page",
            "https://old-name.example.com/globalId/" + instrument.getGlobalIdentifier()));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, PUBLIC_PAGE).getMetadata();

    assertEquals(PUBLIC_PAGE, md.getLandingPage());
  }

  /** The same host-agnostic recognition with nothing to fall back to: omitted, never registered. */
  @Test
  void legacyAutoFilledLandingPageOnAnotherHostIsOmittedWhenNoPublicUrlExists() {
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
   * Neither a field value nor a public landing page: the property is omitted rather than guessed. A
   * LandingPage is baked into a citable PID the moment a curator accepts the record, with no way
   * for RSpace to update a published B2INST record afterwards, so absent is recoverable and
   * wrong-and-published is not. (Whether a missing public URL is caused by an unset server URL is
   * decided by the caller now, and is covered by ApiInventoryDOITest.)
   */
  @Test
  void landingPageIsOmittedWhenThereIsNeitherAFieldNorAPublicUrl() {
    Instrument instrument = templateShapedInstrument();

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, null).getMetadata();

    assertNull(md.getLandingPage());
  }

  /** A Landing page the user typed is registered even when no public landing page is available. */
  @Test
  void userTypedLandingPageIsRegisteredWithoutAPublicUrl() {
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
  void doesNotMapLastCalibratedAndKeepsTheTwoLinksOutOfMeasuredVariable() {
    Instrument instrument = templateShapedInstrument();
    addField(instrument, linkField("Measurement technique", "IsDocumentedBy", "SD101"));
    addField(instrument, linkField("Calibration", "IsCalibratedBy", "SD202"));
    addField(instrument, dateField("Last calibrated", "2026-01-15"));

    B2instInstrumentMetadata md = adapter.buildB2instDoi(instrument, null).getMetadata();

    // The two links reach RelatedIdentifier (ADR 0007), never MeasuredVariable: the narrative
    // mapping ADR 0005 rejected. "Last calibrated" still feeds no PIDINST property at all.
    assertNull(md.getMeasuredVariable());
    assertNull(md.getDate());
    assertEquals(2, md.getRelatedIdentifier().size());
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
    addField(instrument, linkField("Measurement technique", "IsDocumentedBy", "IN114"));

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
    assertEquals("URL", md.at("/RelatedIdentifier/0/relatedIdentifierType").asText());
    assertEquals(
        SERVER + "/globalId/IN114", md.at("/RelatedIdentifier/0/relatedIdentifierValue").asText());
    assertEquals("IsDescribedBy", md.at("/RelatedIdentifier/0/relationType").asText());
    assertEquals(
        "Measurement Technique", md.at("/RelatedIdentifier/0/relatedIdentifierName").asText());
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
    assertTrue(md.at("/RelatedIdentifier").isMissingNode());
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

    DataCiteDoi result = adapter.buildDataCiteDoi(doi, null);

    assertEquals("My DOI", result.getAttributes().getTitles().get(0).getTitle());
  }

  @Test
  void mapsMeasurementTechniqueAndCalibrationLinksIntoRelatedIdentifier() {
    Instrument instrument = templateShapedInstrument();
    addField(instrument, linkField("Measurement technique", "IsDocumentedBy", "IN114"));
    addField(instrument, linkField("Calibration", "IsCalibratedBy", "IN115"));

    B2instDoi doi = adapter.buildB2instDoi(instrument, PUBLIC_PAGE);

    List<B2instRelatedIdentifier> related = doi.getMetadata().getRelatedIdentifier();
    assertEquals(2, related.size());
    // Measurement Technique first, Calibration second, as in the ticket's example payloads.
    // relationType is always IsDescribedBy, whatever the link stores: PIDINST's vocabulary has
    // no IsDocumentedBy/IsCalibratedBy (ADR 0007).
    assertEquals("Measurement Technique", related.get(0).getRelatedIdentifierName());
    assertEquals("IsDescribedBy", related.get(0).getRelationType());
    assertEquals("URL", related.get(0).getRelatedIdentifierType());
    assertEquals(SERVER + "/globalId/IN114", related.get(0).getRelatedIdentifierValue());
    assertEquals("Calibration", related.get(1).getRelatedIdentifierName());
    assertEquals("IsDescribedBy", related.get(1).getRelationType());
    assertEquals("URL", related.get(1).getRelatedIdentifierType());
    assertEquals(SERVER + "/globalId/IN115", related.get(1).getRelatedIdentifierValue());
  }

  @Test
  void relatedIdentifierIsOmittedWhenLinkFieldsAreAbsentOrEmpty() {
    // no link fields at all
    assertNull(
        adapter
            .buildB2instDoi(templateShapedInstrument(), PUBLIC_PAGE)
            .getMetadata()
            .getRelatedIdentifier());

    // fields present but holding no link
    Instrument instrument = templateShapedInstrument();
    InventoryLinkField empty = new InventoryLinkField();
    empty.setName("Measurement technique");
    addField(instrument, empty);
    assertNull(
        adapter.buildB2instDoi(instrument, PUBLIC_PAGE).getMetadata().getRelatedIdentifier());
  }

  /**
   * The blank target is a reachable state; the soft-deleted link is a defensive guard. A link is
   * only ever soft-deleted along with its field ({@code
   * SampleApiManagerImpl.softDeleteLinkOfDeletedLinkField}), and a deleted field is already
   * excluded by {@code getActiveFields}, so no current flow presents a live field holding a deleted
   * link. The guard stays because the entry it would produce is registered permanently, and this
   * pins it against a future change to that lifecycle.
   */
  @Test
  void relatedIdentifierSkipsDeletedLinksAndBlankTargets() {
    Instrument instrument = templateShapedInstrument();
    InventoryLinkField deleted = linkField("Measurement technique", "IsDocumentedBy", "IN114");
    deleted.getLink().setDeleted(true);
    addField(instrument, deleted);
    addField(instrument, linkField("Calibration", "IsCalibratedBy", "  "));

    assertNull(
        adapter.buildB2instDoi(instrument, PUBLIC_PAGE).getMetadata().getRelatedIdentifier());
  }

  @Test
  void relatedIdentifierIsOmittedWhenNoUsableServerUrlExists() {
    when(properties.getServerUrl()).thenReturn("rspace.example.com"); // no scheme
    Instrument instrument = templateShapedInstrument();
    addField(instrument, linkField("Calibration", "IsCalibratedBy", "IN115"));

    assertNull(
        adapter.buildB2instDoi(instrument, PUBLIC_PAGE).getMetadata().getRelatedIdentifier());
  }

  @Test
  void relatedIdentifierFieldNamesMatchCaseInsensitively() {
    Instrument instrument = templateShapedInstrument();
    addField(instrument, linkField("  MEASUREMENT TECHNIQUE ", "IsDocumentedBy", "IN114"));

    List<B2instRelatedIdentifier> related =
        adapter.buildB2instDoi(instrument, PUBLIC_PAGE).getMetadata().getRelatedIdentifier();
    assertEquals(1, related.size());
    assertEquals("Measurement Technique", related.get(0).getRelatedIdentifierName());
  }

  @Test
  void dataCiteDoiCarriesRelatedIdentifiersFromInstrumentLinks() {
    Instrument instrument = templateShapedInstrument();
    addField(instrument, linkField("Measurement technique", "IsDocumentedBy", "IN114"));
    addField(instrument, linkField("Calibration", "IsCalibratedBy", "IN115"));
    ApiInventoryDOI doi = new ApiInventoryDOI();
    doi.setDoi("10.82316/r6m6-v851");
    doi.setTitle("Nico-PIDINST");

    DataCiteDoi result = adapter.buildDataCiteDoi(doi, instrument);

    List<DataCiteDoiAttributes.RelatedIdentifier> related =
        result.getAttributes().getRelatedIdentifiers();
    assertEquals(2, related.size());
    assertEquals("IsDescribedBy", related.get(0).getRelationType());
    assertEquals(SERVER + "/globalId/IN114", related.get(0).getRelatedIdentifier());
    assertEquals("URL", related.get(0).getRelatedIdentifierType());
    assertEquals("Measurement Technique", related.get(0).getRelationTypeInformation());
    assertEquals("IsDescribedBy", related.get(1).getRelationType());
    assertEquals(SERVER + "/globalId/IN115", related.get(1).getRelatedIdentifier());
    assertEquals("URL", related.get(1).getRelatedIdentifierType());
    assertEquals("Calibration", related.get(1).getRelationTypeInformation());
    // the base conversion still happened
    assertEquals("Nico-PIDINST", result.getAttributes().getTitles().get(0).getTitle());
  }

  @Test
  void dataCiteDoiLeavesRelatedIdentifiersNullForNonInstrumentsAndLinklessInstruments() {
    ApiInventoryDOI doi = new ApiInventoryDOI();
    doi.setDoi("10.82316/abc");
    doi.setTitle("a sample");

    assertNull(adapter.buildDataCiteDoi(doi, new Sample()).getAttributes().getRelatedIdentifiers());
    assertNull(adapter.buildDataCiteDoi(doi, null).getAttributes().getRelatedIdentifiers());
    assertNull(
        adapter
            .buildDataCiteDoi(doi, templateShapedInstrument())
            .getAttributes()
            .getRelatedIdentifiers());
  }
}
