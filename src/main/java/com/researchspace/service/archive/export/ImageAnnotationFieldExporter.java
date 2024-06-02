package com.researchspace.service.archive.export;

import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.linkedelements.FieldElementLinkPair;
import com.researchspace.model.EcatImageAnnotation;
import java.io.IOException;
import java.net.URISyntaxException;

class ImageAnnotationFieldExporter extends AbstractFieldExporter<EcatImageAnnotation> {

  ImageAnnotationFieldExporter(FieldExporterSupport support) {
    super(support);
  }

  void createFieldArchiveObject(
      EcatImageAnnotation item, String archiveLink, FieldExportContext context) {
    ArchivalField archiveField = context.getArchiveField();
    ArchivalGalleryMetadata agm = new ArchivalGalleryMetadata();
    agm.setId(item.getId());
    agm.setParentId(archiveField.getFieldId());
    agm.setFileName(archiveLink);
    agm.setLinkFile(archiveLink);
    agm.setName(archiveLink);
    String ext = "png";
    agm.setExtension(ext);
    agm.setContentType("image/" + ext);
    agm.setAnnotation(item.getAnnotations());
    agm.setOriginalId(item.getImageId());
    archiveField.addArchivalAnnotationMetadata(agm);
  }

  String doUpdateLinkText(
      FieldElementLinkPair<EcatImageAnnotation> itemPair,
      String replacementUrl,
      FieldExportContext context) {
    ArchivalField field = context.getArchiveField();
    return updateLink(field.getFieldData(), itemPair.getLink(), replacementUrl);
  }

  @Override
  String getReplacementUrl(FieldExportContext context, EcatImageAnnotation item)
      throws URISyntaxException, IOException {
    String newFileName = getUniqueName("annot_") + ".png";
    byte[] buf = item.getData();
    ArchiveFileUtils.writeToFile(newFileName, buf, context.getRecordFolder());
    return newFileName;
  }
}
