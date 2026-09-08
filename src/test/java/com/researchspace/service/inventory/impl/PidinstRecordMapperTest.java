package com.researchspace.service.inventory.impl;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiPidinstRecord;
import com.researchspace.b2inst.model.response.B2instDraftRecord;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.field.InventoryDateField;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryLinkField;
import com.researchspace.model.inventory.field.InventoryStringField;
import com.researchspace.model.inventory.field.InventoryUriField;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The inverse of the PIDINST mapping: registry records into the unified record, and the unified
 * record onto the default template's fields. Fixtures are trimmed copies of live records read on
 * 2026-09-07 (B2INST tpqdy-6zd98, DataCite 10.15151/esrf-instr-gco8).
 */
class PidinstRecordMapperTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final String B2INST_RECORD =
      "{\"id\":\"tpqdy-6zd98\",\"created\":\"2024-02-07T18:19:24.443130+00:00\","
          + "\"updated\":\"2026-03-06T02:37:01.411557+00:00\",\"is_published\":true,\"status\":\"published\","
          + "\"links\":{\"self_html\":\"https://b2inst.gwdg.de/records/tpqdy-6zd98\"},\"metadata\":{\"Name\":\"Olympus"
          + " IX71 TIRF Structured Light Microscope\","
          + "\"Identifier\":{\"identifierType\":\"Handle\",\"identifierValue\":\"21.11157/44b18238-bba1-4b42-abcc-975017181420\"},\"SchemaVersion\":\"1.0\",\"Description\":\"Inverted"
          + " TIRF microscope for live-cell imaging.\",\"Owner\":[{\"ownerName\":\"Ronghua"
          + " Zughe\"},{\"ownerName\":\"UMass Chan Medical School\","
          + "\"ownerIdentifier\":{\"ownerIdentifierType\":\"Other\",\"ownerIdentifierValue\":\"https://ror.org/0464eyp60\"}}],\"Manufacturer\":[{\"manufacturerName\":\"Karl"
          + " D. Bellve\"},{\"manufacturerName\":\"UMass Chan-Biomedical Imaging Group\"}],"
          + "\"Model\":{\"modelName\":\"IX71\"},\"InstrumentType\":[{\"instrumentTypeName\":\"Inverted"
          + " wide field light microscope\"},{\"instrumentTypeName\":\"Epifluorescence"
          + " microscope\"}],\"MeasuredVariable\":[\"Fluorescent light\",\"Visible light\"],"
          + "\"Date\":[{\"Date\":\"2011-09-30T04:00:00.000Z\",\"dateType\":\"Commissioned\"},"
          + "{\"Date\":\"2025-01-15\",\"dateType\":\"DeCommissioned\"}],"
          + "\"LandingPage\":\"https://trello.com/b/BQ8zCcQC/tirf-microscope\","
          + "\"AlternateIdentifier\":[{\"alternateIdentifierType\":\"Other\",\"alternateIdentifierValue\":\"UMMS-TESM-01\"}]}}";

  private static final String DATACITE_DOI =
      "{\"id\":\"10.15151/esrf-instr-gco8\",\"type\":\"dois\",\"attributes\":{\"doi\":\"10.15151/esrf-instr-gco8\",\"state\":\"findable\",\"publisher\":\"European"
          + " Synchrotron Radiation Facility\",\"titles\":[{\"title\":\"ID21 - X-ray Micro"
          + " Spectroscopy Beamline\"}],\"creators\":[{\"name\":\"European Synchrotron Radiation"
          + " Facility\",\"nameType\":\"Organizational\"}],\"contributors\":[{\"name\":\"ESRF"
          + " Beamline Group\",\"contributorType\":\"HostingInstitution\"},{\"name\":\"Some"
          + " Curator\",\"contributorType\":\"DataCurator\"}],\"descriptions\":[{\"description\":\"Tender"
          + " X-ray micro-spectroscopy"
          + " beamline.\",\"descriptionType\":\"Abstract\"},{\"description\":\"Energy range"
          + " 2.1-10.5 keV\",\"descriptionType\":\"TechnicalInfo\"}],"
          + "\"identifiers\":[{\"identifier\":\"ID21\",\"identifierType\":\"alias\"}],"
          + "\"types\":{\"resourceType\":\"Beamline\",\"resourceTypeGeneral\":\"Instrument\"},"
          + "\"url\":\"https://doi.esrf.fr/10.15151/ESRF-INSTR-GCO8/\"}}";

  private static B2instDraftRecord b2instRecord() throws Exception {
    return JSON.readValue(B2INST_RECORD, B2instDraftRecord.class);
  }

  private static DataCiteDoi dataCiteDoi(String json) throws Exception {
    return JSON.readValue(json, DataCiteDoi.class);
  }

  /** The default template's active fields, in its order, without persistence. */
  private static InstrumentTemplate defaultTemplate() {
    InstrumentTemplate template = new InstrumentTemplate();
    template.setId(7L);
    addField(template, mandatory(new InventoryStringField("Owner")));
    addField(template, mandatory(new InventoryStringField("Manufacturer")));
    addField(template, new InventoryStringField("Model"));
    addField(template, new InventoryStringField("Instrument type"));
    addField(template, new InventoryDateField("Commissioned"));
    addField(template, new InventoryDateField("Decommissioned"));
    addField(template, linkField("Measurement technique"));
    addField(template, new InventoryStringField("Measured quantity"));
    addField(template, linkField("Calibration"));
    addField(template, new InventoryDateField("Last calibrated"));
    addField(template, new InventoryUriField("Landing page"));
    addField(template, new InventoryStringField("Alternate Identifier"));
    return template;
  }

  /**
   * InventoryLinkField has only a no-arg constructor; the name is set through the
   * class-level @Setter.
   */
  private static InventoryLinkField linkField(String name) {
    InventoryLinkField field = new InventoryLinkField();
    field.setName(name);
    return field;
  }

  private static InventoryEntityField mandatory(InventoryEntityField field) {
    field.setMandatory(true);
    return field;
  }

  private static void addField(InstrumentTemplate template, InventoryEntityField field) {
    field.setInventoryRecord(template);
    // columnIndex must be set before adding: refreshActiveFieldsAndColumnIndex() sorts on it
    field.setColumnIndex(template.getFields().size() + 1);
    template.getFields().add(field);
    template.refreshActiveFieldsAndColumnIndex();
  }

  private static String content(ApiInstrument instrument, int index) {
    return instrument.getFields().get(index).getContent();
  }

  @Test
  void b2instRecordMapsEveryPidinstPropertyIntoTheUnifiedRecord() throws Exception {
    ApiPidinstRecord record = PidinstRecordMapper.fromB2inst(b2instRecord());

    assertEquals(IdentifierType.PIDINST_B2INST.name(), record.getProvider());
    assertEquals("21.11157/44b18238-bba1-4b42-abcc-975017181420", record.getPid());
    assertEquals(
        "https://hdl.handle.net/21.11157/44b18238-bba1-4b42-abcc-975017181420",
        record.getPublicUrl());
    assertEquals("https://b2inst.gwdg.de/records/tpqdy-6zd98", record.getProviderRecordUrl());
    assertEquals("accepted", record.getState());
    assertEquals("Olympus IX71 TIRF Structured Light Microscope", record.getName());
    assertEquals(List.of("Ronghua Zughe", "UMass Chan Medical School"), record.getOwners());
    assertEquals(
        List.of("Karl D. Bellve", "UMass Chan-Biomedical Imaging Group"),
        record.getManufacturers());
    assertEquals("IX71", record.getModel());
    assertEquals(
        List.of("Inverted wide field light microscope", "Epifluorescence microscope"),
        record.getInstrumentTypes());
    assertEquals(List.of("Fluorescent light", "Visible light"), record.getMeasuredVariables());
    assertEquals(
        "2011-09-30", record.getCommissioned(), "a timestamp is cut to the date the field accepts");
    assertEquals("2025-01-15", record.getDecommissioned(), "B2INST spells it DeCommissioned");
    assertEquals("https://trello.com/b/BQ8zCcQC/tirf-microscope", record.getLandingPage());
    assertEquals("UMMS-TESM-01", record.getAlternateIdentifier());
    assertEquals("2024-02-07T18:19:24.443130+00:00", record.getCreated());
  }

  @Test
  void dataCiteDoiMapsCreatorsToManufacturersAndHostingInstitutionsToOwners() throws Exception {
    ApiPidinstRecord record = PidinstRecordMapper.fromDataCite(dataCiteDoi(DATACITE_DOI));

    assertEquals(IdentifierType.PIDINST_DATACITE.name(), record.getProvider());
    assertEquals("10.15151/esrf-instr-gco8", record.getPid());
    assertEquals("https://doi.org/10.15151/esrf-instr-gco8", record.getPublicUrl());
    assertEquals(
        "https://commons.datacite.org/doi.org/10.15151/esrf-instr-gco8",
        record.getProviderRecordUrl());
    assertEquals("findable", record.getState());
    assertEquals("ID21 - X-ray Micro Spectroscopy Beamline", record.getName());
    assertEquals(
        "Tender X-ray micro-spectroscopy beamline.",
        record.getDescription(),
        "Abstract wins over TechnicalInfo");
    assertEquals(
        List.of("ESRF Beamline Group"), record.getOwners(), "only HostingInstitution contributors");
    assertEquals(List.of("European Synchrotron Radiation Facility"), record.getManufacturers());
    assertEquals(
        List.of("Beamline"),
        record.getInstrumentTypes(),
        "resourceType unless it is the generic Instrument");
    assertEquals("https://doi.esrf.fr/10.15151/ESRF-INSTR-GCO8/", record.getLandingPage());
    assertEquals("ID21", record.getAlternateIdentifier());
    assertNull(record.getModel());
    assertTrue(record.getMeasuredVariables().isEmpty());
  }

  @Test
  void dataCiteOwnerFallsBackToThePublisherAndGenericResourceTypeIsDropped() throws Exception {
    String noHost =
        DATACITE_DOI
            .replace(
                "\"contributorType\":\"HostingInstitution\"",
                "\"contributorType\":\"RightsHolder\"")
            .replace("\"resourceType\":\"Beamline\"", "\"resourceType\":\"Instrument\"");

    ApiPidinstRecord record = PidinstRecordMapper.fromDataCite(dataCiteDoi(noHost));

    assertEquals(List.of("European Synchrotron Radiation Facility"), record.getOwners());
    assertTrue(record.getInstrumentTypes().isEmpty());
  }

  @Test
  void templateFieldsAreFilledByCanonicalNameAndTypeWithMultiValuesJoined() throws Exception {
    ApiPidinstRecord record = PidinstRecordMapper.fromB2inst(b2instRecord());

    ApiInstrument instrument = PidinstRecordMapper.toApiInstrument(record, defaultTemplate());

    assertEquals(7L, instrument.getTemplateId());
    assertEquals("Olympus IX71 TIRF Structured Light Microscope", instrument.getName());
    assertEquals("Inverted TIRF microscope for live-cell imaging.", instrument.getDescription());
    assertEquals(
        12, instrument.getFields().size(), "one entry per template field, in template order");
    assertEquals("Ronghua Zughe; UMass Chan Medical School", content(instrument, 0));
    assertEquals("Karl D. Bellve; UMass Chan-Biomedical Imaging Group", content(instrument, 1));
    assertEquals("IX71", content(instrument, 2));
    assertEquals(
        "Inverted wide field light microscope; Epifluorescence microscope", content(instrument, 3));
    assertEquals("2011-09-30", content(instrument, 4));
    assertEquals("2025-01-15", content(instrument, 5));
    assertEquals("", content(instrument, 6), "link fields are left to the template's defaults");
    assertEquals("Fluorescent light; Visible light", content(instrument, 7));
    assertEquals("", content(instrument, 9), "Last calibrated has no PIDINST source");
    assertEquals("https://trello.com/b/BQ8zCcQC/tirf-microscope", content(instrument, 10));
    assertEquals("UMMS-TESM-01", content(instrument, 11));
  }

  @Test
  void overlongValuesAreCutToWhatRSpaceStoresAndBadUrlsAndDatesAreDropped() throws Exception {
    ApiPidinstRecord record = PidinstRecordMapper.fromB2inst(b2instRecord());
    record.setName("N".repeat(300));
    record.setDescription("D".repeat(300));
    record.setLandingPage("lab.example.org/no-scheme");
    record.setCommissioned(PidinstRecordMapper.isoDate("September 2011"));

    ApiInstrument instrument = PidinstRecordMapper.toApiInstrument(record, defaultTemplate());

    assertEquals(255, instrument.getName().length());
    assertEquals(250, instrument.getDescription().length());
    assertTrue(instrument.getDescription().endsWith("…"));
    assertNull(PidinstRecordMapper.resolvableUrl("lab.example.org/no-scheme"));
    assertNull(record.getCommissioned());
    assertEquals("", content(instrument, 4));
  }

  @Test
  void aMissingMandatoryValueRefusesTheImportInsteadOfInventingOne() throws Exception {
    ApiPidinstRecord record = PidinstRecordMapper.fromB2inst(b2instRecord());
    record.getManufacturers().clear();

    assertThrows(
        ApiRuntimeException.class,
        () -> PidinstRecordMapper.toApiInstrument(record, defaultTemplate()));
  }

  @Test
  void linkedIdentifierCarriesTheProvidersValueStateUrlsAndTheOriginFlag() throws Exception {
    ApiPidinstRecord record = PidinstRecordMapper.fromB2inst(b2instRecord());
    User user = new User("jane");
    user.setFirstName("Jane");
    user.setLastName("Doe");

    ApiInventoryDOI doi = PidinstRecordMapper.toLinkedIdentifier(record, user);

    assertTrue(doi.isLinked());
    assertTrue(doi.isRegisterIdentifierRequest());
    assertEquals(IdentifierType.PIDINST_B2INST.name(), doi.getDoiType());
    assertEquals("21.11157/44b18238-bba1-4b42-abcc-975017181420", doi.getDoi());
    assertEquals("accepted", doi.getState());
    assertEquals("Olympus IX71 TIRF Structured Light Microscope", doi.getTitle());
    assertEquals(
        "https://hdl.handle.net/21.11157/44b18238-bba1-4b42-abcc-975017181420", doi.getPublicUrl());
    assertEquals("https://b2inst.gwdg.de/records/tpqdy-6zd98", doi.getProviderUrl());
    assertEquals("Instrument", doi.getResourceTypeGeneral());
    // getURLSafeSecureRandomString takes a byte count, so 16 bytes is a 22-char base64url
    // suffix; what matters here is only that one was generated, as the attach path
    // refuses a blank suffix
    assertTrue(isNotBlank(doi.getPublicLinkSuffix()), "the attach path refuses a blank suffix");
  }
}
