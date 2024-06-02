package com.researchspace.service.archive.export;

import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.linkedelements.FieldElementLinkPair;
import com.researchspace.model.EcatImage;
import java.io.IOException;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
class ImageFieldExporter extends AbstractFieldExporter<EcatImage> {

  ImageFieldExporter(FieldExporterSupport support) {
    super(support);
  }

  ImageFieldExporter() {}

  void createFieldArchiveObject(EcatImage item, String archiveLink, FieldExportContext context) {
    ArchivalField field = context.getArchiveField();
    ArchivalGalleryMetadata agm = archiveModelFactory.createGalleryMetadata(item);
    agm.setParentId(field.getFieldId());
    agm.setLinkFile(archiveLink);

    // if image is a tiff, then export original file along the working copy
    String originalLink = new RelativeLinkProcessor(context, support).getOriginalFile(item);
    if (!StringUtils.isEmpty(originalLink)) {
      agm.setLinkToOriginalFile(originalLink);
    }
    field.addArchivalImageMetadata(agm);
  }

  String doUpdateLinkText(
      FieldElementLinkPair<EcatImage> itemPair, String replacementUrl, FieldExportContext context) {
    ArchivalField field = context.getArchiveField();
    return support
        .getRichTextUpdater()
        .replaceImageSrcURL(
            field.getFieldId() + "-" + itemPair.getElement().getId(),
            field.getFieldData(),
            replacementUrl);
  }

  @Override
  String getReplacementUrl(FieldExportContext context, EcatImage item)
      throws URISyntaxException, IOException {
    return new RelativeLinkProcessor(context, support)
        .getLinkReplacement(item)
        .getRelativeLinkToReplaceLinkInText();
  }
}
