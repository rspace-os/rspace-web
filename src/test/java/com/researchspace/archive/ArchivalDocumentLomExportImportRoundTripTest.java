package com.researchspace.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.archive.elninventory.ArchivalListOfMaterials;
import com.researchspace.archive.elninventory.ArchivalMaterialUsage;
import com.researchspace.model.elninventory.MaterialUsage;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.testutils.ArchiveTestUtils;
import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * Guards that an ELN document whose List of Materials references an instrument (and other inventory
 * objects) stays importable after the LoM XML change (RSDEV-1032): the new {@code globalId} element
 * must be present in the schema that {@code /export/importArchive} validates against.
 *
 * <p>{@link ArchiveTestUtils#writeToXMLAndReadFromXML} reproduces the export/import round-trip
 * exactly: it marshals the document to XML, generates the XSD from {@code ArchivalDocument.class}
 * (the same call the real exporter makes in {@code XMLArchiveExportManagerServiceImpl}), then
 * unmarshals validating against that generated schema. It uses the default JAXB validation handler,
 * which throws on the first schema error - stricter than the import-time {@code
 * XMLImportSchemaValidator}, which only records the error. So if this round-trip succeeds, import
 * validation cannot reject the new field.
 */
public class ArchivalDocumentLomExportImportRoundTripTest {

  @Test
  public void documentWithInstrumentLomRoundTripsAndValidatesAgainstGeneratedSchema()
      throws Exception {
    Instrument instrument = new Instrument();
    instrument.setId(42L);
    SubSample subSample = new SubSample();
    subSample.setId(7L);

    ArchivalListOfMaterials lom = new ArchivalListOfMaterials();
    lom.setName("instrument LoM");
    lom.setDescription("references an instrument and a subsample");
    lom.setOriginalElnFieldId(1L);
    lom.getMaterials().add(new ArchivalMaterialUsage(new MaterialUsage(null, instrument, null)));
    lom.getMaterials().add(new ArchivalMaterialUsage(new MaterialUsage(null, subSample, null)));

    ArchivalField field = new ArchivalField();
    field.getListsOfMaterials().add(lom);

    ArchivalDocument doc = new ArchivalDocument();
    doc.setDocId(1L);
    doc.setName("doc with instrument LoM");
    doc.setType("NORMAL");
    doc.setCreatedBy("user1a");
    doc.setCreationDate(new Date(0L));
    doc.setLastModifiedDate(new Date(0L));
    doc.setVersion(1L);
    doc.addArchivalField(field);

    ArchivalDocument roundTripped =
        ArchiveTestUtils.writeToXMLAndReadFromXML(doc, ArchivalDocument.class);

    assertNotNull(roundTripped);
    ArchivalListOfMaterials importedLom =
        roundTripped.getListFields().get(0).getListsOfMaterials().get(0);
    assertEquals(2, importedLom.getMaterials().size());

    ArchivalMaterialUsage importedInstrument = importedLom.getMaterials().get(0);
    assertEquals("IN42", importedInstrument.getGlobalId());
    assertEquals("INSTRUMENT", importedInstrument.getInvRecType());
    assertEquals(2, importedInstrument.getSchemaVersion());

    // the "Could" requirement: global IDs for the other inventory objects survive too
    ArchivalMaterialUsage importedSubSample = importedLom.getMaterials().get(1);
    assertEquals("SS7", importedSubSample.getGlobalId());
    assertEquals("SUBSAMPLE", importedSubSample.getInvRecType());
  }
}
