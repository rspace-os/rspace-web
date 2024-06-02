package com.researchspace.service.archive.export;

import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.linkedelements.FieldElementLinkPair;
import com.researchspace.model.FileProperty;
import com.researchspace.model.RSChemElement;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import org.apache.commons.io.FileUtils;

class RsChemElementFieldExporter extends AbstractFieldExporter<RSChemElement> {

  RsChemElementFieldExporter(FieldExporterSupport support) {
    super(support);
  }

  RsChemElementFieldExporter() {}

  @Override
  void createFieldArchiveObject(RSChemElement item, String newLink, FieldExportContext context) {
    ArchivalGalleryMetadata agm = new ArchivalGalleryMetadata();
    ArchivalField archiveField = context.getArchiveField();

    agm.setId(item.getId());
    agm.setParentId(archiveField.getFieldId());
    agm.setFileName(newLink);
    agm.setLinkFile(newLink);
    agm.setName(newLink);
    agm.setExtension("png");
    agm.setContentType("image/png");
    agm.setAnnotation(item.getChemElements());
    agm.setChemElementsFormat(item.getChemElementsFormat().name());
    if (item.getImageFileProperty() != null) {
      agm.setLinkToOriginalFile(getLargePreviewUrlForReplacementUrl(newLink));
    }
    archiveField.addArchivalChemdooleMetadata(agm);
  }

  String doUpdateLinkText(
      FieldElementLinkPair<RSChemElement> chemPair,
      String archiveLink,
      FieldExportContext context) {
    boolean largePreviewPresent = chemPair.getElement().getImageFileProperty() != null;
    String imageLink =
        largePreviewPresent ? getLargePreviewUrlForReplacementUrl(archiveLink) : archiveLink;

    return support
        .getRichTextUpdater()
        .replaceImageSrc(chemPair.getLink(), imageLink, context.getArchiveField().getFieldData());
  }

  String getLargePreviewUrlForReplacementUrl(String replacementUrl) {
    return "large_" + replacementUrl;
  }

  @Override
  String getReplacementUrl(FieldExportContext context, RSChemElement item)
      throws URISyntaxException, IOException {
    String smallPreviewFileName = getUniqueName("chemd_") + ".png";
    byte[] buf = item.getDataImage();
    ArchiveFileUtils.writeToFile(smallPreviewFileName, buf, context.getRecordFolder());

    // RSPAC-1915 export large preview if present
    FileProperty imageFileProperty = item.getImageFileProperty();
    if (imageFileProperty != null) {
      File fileToExport = support.getFileStore().findFile(imageFileProperty);
      File largePreviewFile =
          new File(
              context.getRecordFolder(), getLargePreviewUrlForReplacementUrl(smallPreviewFileName));
      FileUtils.copyFile(fileToExport, largePreviewFile);
    }
    return smallPreviewFileName;
  }
}
