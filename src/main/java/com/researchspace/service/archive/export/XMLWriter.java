package com.researchspace.service.archive.export;

import com.researchspace.archive.ArchivalDocument;
import com.researchspace.archive.ArchivalForm;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.archive.model.ArchiveModelFactory;
import com.researchspace.core.util.XMLReadWriteUtils;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class XMLWriter implements ExportObjectWriter {

  private Logger logger = LoggerFactory.getLogger(XMLWriter.class);

  @Override
  public void writeExportObject(File outputFile, ExportedRecord exported) {
    try {
      if (exported.getExportedRecord().isStructuredDocument()) {
        marshalDocument(outputFile, exported.getArchivedRecord());
        StructuredDocument doc = (StructuredDocument) exported.getExportedRecord();
        marshalDocumentForm(doc.getForm(), outputFile.getParentFile());
      } else if (exported.getExportedRecord().isMediaRecord()) {
        marshalMediaDoc(outputFile, exported.getArchivedMedia());
      }
    } catch (Exception e) {
      // log here as will run on bg thread and exceptions get squashed
      logger.error("Error writing XML files: " + e.getMessage());
      if (e.getCause() != null) {
        logger.error("Underlying cause: " + e.getCause().getMessage());
      }
      throw new ExportFailureException(e.getMessage());
    }
  }

  private void marshalDocumentForm(RSForm form, File recordFolder) throws Exception {
    String formCode = recordFolder.getName() + "_form";
    ArchivalForm archiveForm = new ArchiveModelFactory().createArchivalForm(form, formCode);
    archiveForm.setCode(formCode);
    File formFile = new File(recordFolder, formCode + ".xml");
    if (!formFile.exists()) {
      makeFormXML(formFile, archiveForm);
    }
  }

  // marshals the populates ArchivalDocument to XML
  private void marshalDocument(File xmlFile, ArchivalDocument archiveDoc) throws Exception {
    XMLReadWriteUtils.toXML(xmlFile, archiveDoc, ArchivalDocument.class);
  }

  private void marshalMediaDoc(File outputFile, ArchivalGalleryMetadata archivedMedia)
      throws Exception {
    XMLReadWriteUtils.toXML(outputFile, archivedMedia, ArchivalGalleryMetadata.class);
  }

  private void makeFormXML(File ouF, ArchivalForm fm) throws Exception {
    XMLReadWriteUtils.toXML(ouF, fm, ArchivalForm.class);
  }
}
