package com.researchspace.service.archive.export;

import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.linkedelements.FieldElementLinkPair;
import com.researchspace.model.EcatImageAnnotation;
import java.io.IOException;
import java.net.URISyntaxException;

class SketchFieldExporter extends AbstractFieldExporter<EcatImageAnnotation> {

  SketchFieldExporter(FieldExporterSupport support) {
    super(support);
  }

  void createFieldArchiveObject(
      EcatImageAnnotation sketch, String archiveLink, FieldExportContext context) {
    ArchivalGalleryMetadata agm = new ArchivalGalleryMetadata();
    ArchivalField archiveField = context.getArchiveField();
    agm.setId(sketch.getId());
    agm.setParentId(archiveField.getFieldId());
    agm.setFileName(archiveLink);
    agm.setLinkFile(archiveLink);
    agm.setName(archiveLink);
    agm.setExtension("png");
    agm.setContentType("image/png");
    agm.setAnnotation(sketch.getAnnotations());
    archiveField.addArchivalSketchMetadata(agm);
  }

  String doUpdateLinkText(
      FieldElementLinkPair<EcatImageAnnotation> itemPair,
      String replacementUrl,
      FieldExportContext context) {
    return updateLink(context.getArchiveField().getFieldData(), itemPair.getLink(), replacementUrl);
  }

  @Override
  String getReplacementUrl(FieldExportContext context, EcatImageAnnotation sketch)
      throws URISyntaxException, IOException {
    String newFileName = getUniqueName("sktch_") + ".png";
    byte[] buf = sketch.getData();
    ArchiveFileUtils.writeToFile(newFileName, buf, context.getRecordFolder());
    return newFileName;
  }
}
