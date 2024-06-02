package com.researchspace.service.archive.export;

import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.linkedelements.FieldElementLinkPair;
import com.researchspace.model.RSMath;
import java.io.IOException;
import java.net.URISyntaxException;

class MathFieldExporter extends AbstractFieldExporter<RSMath> {

  MathFieldExporter(FieldExporterSupport support) {
    super(support);
  }

  MathFieldExporter() {}

  void createFieldArchiveObject(RSMath math, String newLink, FieldExportContext context) {
    ArchivalGalleryMetadata agm = new ArchivalGalleryMetadata();
    ArchivalField archiveField = context.getArchiveField();
    agm.setId(math.getId());
    agm.setParentId(archiveField.getFieldId());
    agm.setFileName(newLink);
    agm.setLinkFile(newLink);
    agm.setName(newLink);
    agm.setExtension("svg");
    agm.setContentType(MediaUtils.SVG_XML);
    agm.setAnnotation(math.getLatex());
    archiveField.addArchivalMathMetadata(agm);
  }

  String doUpdateLinkText(
      FieldElementLinkPair<RSMath> mathx, String archiveLink, FieldExportContext context) {
    return support
        .getRichTextUpdater()
        .replaceObjectDataURL(
            mathx.getLink(), archiveLink, context.getArchiveField().getFieldData());
  }

  @Override
  String getReplacementUrl(FieldExportContext context, RSMath item)
      throws URISyntaxException, IOException {
    String fileName = getUniqueName("math_") + ".svg";
    byte[] buf = item.getMathSvg().getData();
    ArchiveFileUtils.writeToFile(fileName, buf, context.getRecordFolder());
    return fileName;
  }
}
