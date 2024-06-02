package com.researchspace.service.archive.export;

import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.archive.ArchiveFileNameData;
import com.researchspace.archive.ArchiveUtils;
import com.researchspace.archive.ImmutableExportRecordList;
import com.researchspace.linkedelements.FieldElementLinkPair;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import java.io.IOException;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class LinkedRecordFieldExporter extends AbstractFieldExporter<RecordInformation> {

  private ImmutableExportRecordList exportList;

  LinkedRecordFieldExporter(FieldExporterSupport support, ImmutableExportRecordList exportList) {
    super(support);
    this.exportList = exportList;
  }

  public boolean export(
      FieldExportContext context, FieldElementLinkPair<RecordInformation> itemPair) {
    try {

      RecordInformation linkedRecInfo = itemPair.getElement();
      GlobalIdentifier linkedRecGlobalId = linkedRecInfo.getOid();
      Long linkedRecId = linkedRecInfo.getId();
      BaseRecord latestLinkedRecord;
      if (support.isRecord(linkedRecId)) {
        latestLinkedRecord = support.getDocumentById(linkedRecId);
      } else {
        // rspac-1136 populating latest parent
        latestLinkedRecord = support.getFolderById(linkedRecId);
      }

      ArchivalField archiveField = context.getArchiveField();
      if (latestLinkedRecord.isFolder()) {
        // rspac-657 folder/notebook export
        exportFolderOrNotebookLink(context, latestLinkedRecord, linkedRecInfo, archiveField);
        return true;
      }

      // if the linked document is exported update the link to point inside archive
      if (isLinkedDocumentOnExportList(linkedRecGlobalId, exportList)
          || (latestLinkedRecord.isIdentifiedByOid(linkedRecGlobalId)
              && isLinkedDocumentOnExportList(latestLinkedRecord.getOid(), exportList))) {

        // use same naming mechanism as for unlinked records - jira 273
        // if revision is null, we're not exporting revision history, so
        // don't want revision number in folder names. rspac1329
        StructuredDocument linkedDoc = latestLinkedRecord.asStrucDoc();

        Number revisionNum = null;
        if (context.getRevision() != null) {
          AuditedEntity<StructuredDocument> docRevision =
              support
                  .getAuditManager()
                  .getNewestRevisionForEntity(StructuredDocument.class, linkedRecId);
          revisionNum = docRevision.getRevision();
        } else if (linkedRecGlobalId.hasVersionId()
            && !latestLinkedRecord.isIdentifiedByOid(linkedRecGlobalId)) {
          // version export
          revisionNum =
              support
                  .getAuditManager()
                  .getRevisionNumberForDocumentVersion(
                      linkedDoc.getId(), linkedRecGlobalId.getVersionId());
        }

        String linkedDocArchiveFileName =
            new ArchiveFileNameData(linkedDoc, revisionNum).toFileName();

        // update the link so it point to record in the archive
        String fileName =
            context.getConfig().generateDocumentExportFileName(linkedDocArchiveFileName);
        String fielddata =
            support
                .getRichTextUpdater()
                .replaceLinkedRecordURL(
                    linkedRecGlobalId,
                    archiveField.getFieldData(),
                    "../" + linkedDocArchiveFileName + "/" + fileName);
        archiveField.setFieldData(fielddata);
        createFieldArchiveObject(linkedRecInfo, linkedDocArchiveFileName, context);
      } else {
        // replace with global link if not going to be included in export RSPAC-1330.
        // i.e. treat as external link, not included in ArchivalGalleryMetadata
        replaceInternalLinkInArchiveFieldWithAbsoluteGlobalLink(archiveField, linkedRecInfo);
      }
    } catch (Exception e) {
      log.warn("linkedRecordExport: Error on record links output: " + e.getMessage());
      return false;
    }
    return true;
  }

  private void exportFolderOrNotebookLink(
      FieldExportContext context,
      BaseRecord linkedRec,
      RecordInformation linkedRecInfo,
      ArchivalField archiveField) {

    if (isLinkedFolderOnExportList(linkedRecInfo.getId(), exportList)) {
      String linkedDocArchiveFileName = new ArchiveFileNameData(linkedRec, null).toFileName();
      String fileName =
          context.getConfig().generateDocumentExportFileName(linkedDocArchiveFileName);
      String fielddata =
          support
              .getRichTextUpdater()
              .replaceLinkedRecordURL(
                  linkedRecInfo.getOid(), archiveField.getFieldData(), "../" + fileName);
      archiveField.setFieldData(fielddata);
      createFieldArchiveObject(linkedRecInfo, fileName, context);
    } else {
      replaceInternalLinkInArchiveFieldWithAbsoluteGlobalLink(archiveField, linkedRecInfo);
    }
  }

  private void replaceInternalLinkInArchiveFieldWithAbsoluteGlobalLink(
      ArchivalField archiveField, RecordInformation item) {
    String fielddata =
        support
            .getRichTextUpdater()
            .replaceLinkedRecordURL(
                item.getOid(),
                archiveField.getFieldData(),
                ArchiveUtils.getAbsoluteGlobalLink(
                    item.getOid().getIdString(), support.getServerUrl()));
    archiveField.setFieldData(fielddata);
  }

  private boolean isLinkedDocumentOnExportList(
      GlobalIdentifier rcdId, ImmutableExportRecordList exportList) {
    return exportList.containsRecord(rcdId);
  }

  private boolean isLinkedFolderOnExportList(Long id, ImmutableExportRecordList exportList) {
    return exportList.containsFolder(id);
  }

  @Override
  void createFieldArchiveObject(
      RecordInformation item, String archiveLink, FieldExportContext context) {
    ArchivalGalleryMetadata agm = new ArchivalGalleryMetadata();
    agm.setId(item.getId());
    agm.setParentId(context.getArchiveField().getFieldId());
    agm.setFileName(item.getName());
    agm.setLinkFile(archiveLink);
    agm.setExtension(item.getExtension());

    GlobalIdPrefix oidPrefix = item.getOid().getPrefix();
    String contentType;
    if (GlobalIdPrefix.FL.equals(oidPrefix) || GlobalIdPrefix.GF.equals(oidPrefix)) {
      contentType = "Link/FOLDER";
    } else if (GlobalIdPrefix.NB.equals(oidPrefix)) {
      contentType = "Link/NOTEBOOK";
    } else {
      contentType = "Link/" + item.getExtension();
    }
    agm.setContentType(contentType);
    context.getArchiveField().addArchivalLinkMetadata(agm);
  }

  @Override
  String doUpdateLinkText(
      FieldElementLinkPair<RecordInformation> itemPair,
      String replacementUrl,
      FieldExportContext context) {
    return "";
  }

  @Override
  String getReplacementUrl(FieldExportContext context, RecordInformation item)
      throws URISyntaxException, IOException {
    return "";
  }
}
