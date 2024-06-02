package com.researchspace.service.archive.export;

import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.linkedelements.FieldElementLinkPair;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.record.BaseRecord;
import java.io.IOException;
import java.net.URISyntaxException;

class AttachmentFieldExporter extends AbstractFieldExporter<EcatMediaFile> {

  AttachmentFieldExporter(FieldExporterSupport support) {
    super(support);
  }

  AttachmentFieldExporter() {}

  void createFieldArchiveObject(EcatMediaFile item, String newLink, FieldExportContext context) {
    ArchivalField archiveField = context.getArchiveField();
    ArchivalGalleryMetadata archiveGalleryMeta = archiveModelFactory.createGalleryMetadata(item);
    archiveGalleryMeta.setParentId(archiveField.getFieldId());
    archiveGalleryMeta.setLinkFile(newLink);

    if (item.isAudio()) {
      archiveField.addArchivalAudioMetadata(archiveGalleryMeta);
    } else if (item.isVideo()) {
      archiveField.addArchivalVideoMetadata(archiveGalleryMeta);
    } else if (item.isChemistryFile()) {
      archiveField.addArchivalChemFileMetadata(archiveGalleryMeta);
    } else if (item.isMediaRecord()) {
      archiveField.addArchivalAttachMetadata(archiveGalleryMeta);
    }
  }

  String doUpdateLinkText(
      FieldElementLinkPair<EcatMediaFile> mediaFilePair,
      String archiveLink,
      FieldExportContext context) {
    String fieldData = "";
    EcatMediaFile item = mediaFilePair.getElement();
    ArchivalField archiveField = context.getArchiveField();

    if (!context.getConfig().isArchive() && item.isAV()) {
      String compId = BaseRecord.getCompositeId(item, archiveField.getFieldId());
      fieldData =
          support
              .getRichTextUpdater()
              .replaceAVTableWithLinkToResource(
                  archiveField.getFieldData(), compId, archiveLink, item.getName());
    } else if (item.isChemistryFile()) {
      if (!context.getConfig().isArchive()) {
        fieldData =
            support
                .getRichTextUpdater()
                .insertHrefToChemistryFile(
                    archiveField.getFieldData(),
                    item.getId().toString(),
                    archiveLink,
                    item.getName());
      } else {
        // Export is xml return normal chem img link
        fieldData = archiveField.getFieldData();
      }
    } else {
      fieldData = updateLink(archiveField.getFieldData(), mediaFilePair.getLink(), archiveLink);
    }
    return fieldData;
  }

  @Override
  String getReplacementUrl(FieldExportContext context, EcatMediaFile item)
      throws URISyntaxException, IOException {
    return new RelativeLinkProcessor(context, support)
        .getLinkReplacement(item)
        .getRelativeLinkToReplaceLinkInText();
  }
}
