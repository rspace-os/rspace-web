package com.researchspace.service.archive.export;

import com.researchspace.archive.ArchiveFolder;
import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.dao.RecordDao;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.linkedelements.FieldElementLinkPair;
import com.researchspace.linkedelements.FieldElementLinkPairs;
import com.researchspace.linkedelements.FieldParser;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.Group;
import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.dtos.ExportSelection;
import com.researchspace.model.permissions.IGroupPermissionUtils;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.AuditManager;
import com.researchspace.service.FolderManager;
import com.researchspace.service.GroupManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.UserManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

/*
 * mostly refactored out of ExportImport and AbstractArchiveExporter, for RSPAC-1711
 */
@Slf4j
public class ArchiveExportPlannerImpl implements ArchiveExportPlanner {

  private @Autowired RecordManager recordManager;
  private @Autowired IPermissionUtils permissions;
  private @Autowired FolderManager folderManager;
  private @Autowired FieldParser fieldParser;
  private @Autowired IPermissionUtils permUtils;
  protected @Autowired RecordDao recordDao;
  private @Autowired UserManager userManager;
  private @Autowired AuditManager auditManager;
  private @Autowired IGroupPermissionUtils groupPermUtils;
  private @Autowired @Lazy GroupManager grpMgr;

  @Override
  public ExportRecordList createExportRecordList(
      IArchiveExportConfig exportConfig, ExportSelection exportSelection) {
    StopWatch sw = StopWatch.createStarted();
    User exporter = exportConfig.getExporter();
    List<Long> ids = new ArrayList<>();
    List<String> types = new ArrayList<>();
    populateExportIdsAndTypesFromSelection(exportSelection, exporter, ids, types);
    Long[] exportIds = ids.toArray(new Long[0]);
    String[] exportTypes = types.toArray(new String[0]);

    verifyInput(exportIds, exportTypes);
    ExportIdCollector exportIdCollector = new ExportIdCollector(folderManager, exportConfig);
    ExportRecordList rcdList =
        exportIdCollector.getRecordsToArchive(exportIds, exportTypes, exporter);

    // Checking whether exporter has READ permissions for all initial items to export
    for (GlobalIdentifier recordOid : rcdList.getRecordsToExport()) {
      if (!permissions.isPermitted(
          recordManager.get(recordOid.getDbId()), PermissionType.READ, exporter)) {
        throw new ExportFailureException(
            String.format(
                "Unauthorized attempt by : %s to export record with id %d",
                exporter.getUsername(), recordOid.getDbId()));
      }
    }
    // Checking whether exporter has READ permissions for all folders
    for (ArchiveFolder archiveFolder : rcdList.getFolderTree()) {
      try {
        if (!permissions.isPermitted(
            folderManager.getFolder(archiveFolder.getId(), exporter),
            PermissionType.READ,
            exporter)) {
          throw new ExportFailureException(
              String.format(
                  "Unauthorized attempt by : %s to export folder with id %d",
                  exporter.getUsername(), archiveFolder.getId()));
        }
      } catch (AuthorizationException e) {
        throw new ExportFailureException(
            String.format(
                "Unauthorized attempt by : %s to export folder with id %d",
                exporter.getUsername(), archiveFolder.getId()));
      }
    }

    log.info("Scanning items took {} ms", sw.getTime());
    sw.reset();
    sw.start();
    updateExportListWithLinkedRecords(rcdList, exportConfig);
    // this will ensure attachments of linked shared documents are included as media_ files
    // RSPAC-2493
    if (exportConfig.isGroupScope() || exportConfig.isUserScope()) {
      int i = 0;
      for (GlobalIdentifier gid : rcdList.getAssociatedFieldAttachments()) {
        // NF file-system links can also be in getAssociatedFieldAttachments
        if (!rcdList.containsRecord(gid) && GlobalIdPrefix.GL.equals(gid.getPrefix())) {
          rcdList.add(gid);
          i++;
        }
      }
      log.info("{} shared record attachments were added to export", i);
    }
    log.info("Scanning linked records took {} ms", sw.getTime());

    return rcdList;
  }

  @Override
  public void updateExportListWithLinkedRecords(
      ExportRecordList exportList, IArchiveExportConfig aconfig) {

    // ids to export list, initially contains just user-selected records to export
    List<GlobalIdentifier> idsToExport = new ArrayList<>();
    idsToExport.addAll(exportList.getRecordsToExport());

    // expand ids to export list with linked items
    for (GlobalIdentifier currRecordId : exportList.getRecordsToExport()) {
      if (aconfig.getMaxLinkLevel() > 0) {
        scanLinkedRecordsAndAttachments(exportList, idsToExport, aconfig, currRecordId, 0);
      } else {
        scanAttachmentsOnly(exportList, idsToExport, aconfig, currRecordId);
      }
    }

    // add found linked records to export list
    idsToExport.removeAll(exportList.getRecordsToExport());
    exportList.addAll(idsToExport);
  }

  private void scanLinkedRecordsAndAttachments(
      ExportRecordList exportList,
      List<GlobalIdentifier> idsToExport,
      IArchiveExportConfig aconfig,
      GlobalIdentifier currentRecordOid,
      int currentLinkLevel) {

    int maxLinkLevel = aconfig.getMaxLinkLevel();
    if (currentLinkLevel >= maxLinkLevel) {
      return;
    }
    textFieldStream(currentRecordOid, aconfig)
        .forEach(
            fieldContents -> {
              FieldElementLinkPairs<RecordInformation> links =
                  fieldContents.getLinkedRecordsWithRelativeUrl();
              addLinkedRecordsToExportList(
                  exportList, idsToExport, aconfig, currentLinkLevel, maxLinkLevel, links);
              addFieldAttachments(exportList, aconfig, fieldContents);
            });
  }

  private void scanAttachmentsOnly(
      ExportRecordList exportList,
      List<GlobalIdentifier> idsToExport,
      IArchiveExportConfig aconfig,
      GlobalIdentifier currentRecordOid) {
    textFieldStream(currentRecordOid, aconfig)
        .forEach(
            fieldContents -> {
              addFieldAttachments(exportList, aconfig, fieldContents);
            });
  }

  private void addFieldAttachments(
      ExportRecordList exportList, IArchiveExportConfig aconfig, FieldContents fieldContents) {
    List<GlobalIdentifier> fieldElements = getPermittedFieldContents(fieldContents, aconfig);
    exportList.addAllFieldAttachments(fieldElements);
  }

  // get stream of parsed field contents for a document
  Stream<FieldContents> textFieldStream(
      GlobalIdentifier currentRecordOid, IArchiveExportConfig aconfig) {
    Record currentRecord = recordDao.get(currentRecordOid.getDbId());
    List<AuditedRecord> versionsToExport =
        getVersionsToExportForRecord(aconfig, currentRecordOid, currentRecord);
    return versionsToExport.stream()
        .map(AuditedRecord::getRecord)
        .filter(BaseRecord::isStructuredDocument)
        .map(BaseRecord::asStrucDoc)
        .flatMap(sd -> sd.getTextFields().stream())
        .map(field -> fieldParser.findFieldElementsInContent(field.getFieldData()));
  }

  private List<GlobalIdentifier> getPermittedFieldContents(
      FieldContents fieldContents, IArchiveExportConfig aconfig) {
    FieldElementLinkPairs<IFieldLinkableElement> allLinks = fieldContents.getAllLinks();
    log.debug(
        "checking permissions for {}",
        allLinks.getElements().stream()
            .map(id -> id.getOid().toString())
            .collect(Collectors.joining(",")));
    List<GlobalIdentifier> permittedFieldElements =
        allLinks.getElements().stream()
            .filter(
                p ->
                    permUtils.isPermittedViaMediaLinksToRecords(
                        p, PermissionType.READ, aconfig.getExporter()))
            .map(IFieldLinkableElement::getOid)
            .collect(Collectors.toList());
    log.debug(
        "allowed items are {}",
        permittedFieldElements.stream()
            .map(GlobalIdentifier::toString)
            .collect(Collectors.joining(",")));
    return permittedFieldElements;
  }

  private void addLinkedRecordsToExportList(
      ExportRecordList exportList,
      List<GlobalIdentifier> idsToExport,
      IArchiveExportConfig aconfig,
      int currentLinkLevel,
      int maxLinkLevel,
      FieldElementLinkPairs<RecordInformation> links) {
    for (FieldElementLinkPair<RecordInformation> itemPair : links.getPairs()) {

      RecordInformation linkedRecInfo = itemPair.getElement();
      Long linkedRecId = linkedRecInfo.getId();
      GlobalIdentifier linkedRecGlobalId = linkedRecInfo.getOid();

      if (recordDao.isRecord(linkedRecId)) {
        Record linkedRec = recordDao.get(linkedRecId);
        if (!permUtils.isPermitted(linkedRec, PermissionType.READ, aconfig.getExporter())) {
          log.warn(
              "linkedRecordExport: excluding [{}], no read permission",
              linkedRec.getGlobalIdentifier());
          continue; // rspac-1123
        }
        if (!idsToExport.contains(linkedRecGlobalId)) {
          idsToExport.add(linkedRecGlobalId);

          if (currentLinkLevel + 1 < maxLinkLevel) {
            scanLinkedRecordsAndAttachments(
                exportList, idsToExport, aconfig, linkedRecGlobalId, currentLinkLevel + 1);
          } else {
            scanAttachmentsOnly(exportList, idsToExport, aconfig, linkedRecGlobalId);
          }
        }
      }
    }
  }

  private void populateExportIdsAndTypesFromSelection(
      ExportSelection exportSelection, User exporter, List<Long> ids, List<String> types) {

    if (ExportSelection.ExportType.USER.equals(exportSelection.getType())) {
      User toExport = userManager.getUserByUsername(exportSelection.getUsername());
      Folder rootRecord = folderManager.getRootFolderForUser(toExport);
      if (rootRecord == null) {
        throw new IllegalStateException(
            "User "
                + toExport.getFullName()
                + "'s account has not been initialised - there is nothing to archive!");
      }
      ids.add(rootRecord.getId());
      types.add(rootRecord.getType());

    } else if (ExportSelection.ExportType.GROUP.equals(exportSelection.getType())) {
      Group grp = grpMgr.getGroup(exportSelection.getGroupId());
      checkGroupExportPermissions(exporter, grp);
      getGroupMembersRootFolderIds(grp, exporter, ids, types, null);

    } else if (ExportSelection.ExportType.SELECTION.equals(exportSelection.getType())) {
      ids.addAll(Arrays.asList(exportSelection.getExportIds()));
      types.addAll(Arrays.asList(exportSelection.getExportTypes()));
    }
  }

  public void getGroupMembersRootFolderIds(
      Group grp, User exporter, List<Long> ids, List<String> types, List<String> names) {
    for (User user : grp.getMembers()) {
      Folder root = folderManager.getRootRecordForUser(user, user);

      if (permissions.isPermitted(root, PermissionType.READ, exporter)) {
        ids.add(root.getId());
        types.add(root.getType());
        if (names != null) {
          names.add(root.getName());
        }
      } else {
        log.warn(
            "While exporting {} group's work by exporter {} user {} was skipped due to"
                + " permissions.",
            grp.getUniqueName(),
            exporter.getUsername(),
            root.getName());
      }
    }
  }

  @Override
  public void checkGroupExportPermissions(User exporter, Group group) {
    if (!groupPermUtils.userCanExportGroup(exporter, group)) {
      throw new AuthorizationException(exporter.getUsername() + " unauthorised to export group");
    }
  }

  void verifyInput(Long[] exportIds, String[] exportTypes) {
    int x1 = exportIds.length, x3 = exportTypes.length;
    int dx2 = x1 - x3;
    if (dx2 > 0) {
      for (int i = x3; i < x1; i++) {
        exportTypes[i] = RecordType.NORMAL.name();
      }
    }
  }

  @SuppressWarnings("unchecked")
  public List<AuditedRecord> getVersionsToExportForRecord(
      IArchiveExportConfig aconfig, GlobalIdentifier recordToExportOid, Record latestRecord) {

    List<AuditedRecord> versionsToExport = new ArrayList<>();
    if (aconfig.isAllVersion()) {
      if (latestRecord.isStructuredDocument()) {
        StructuredDocument strucDoc = latestRecord.asStrucDoc();
        List<AuditedRecord> historyListAll = auditManager.getHistory(strucDoc, null);
        for (AuditedRecord ar : historyListAll) {
          AuditedRecord auditedRecord =
              auditManager.getDocumentRevisionOrVersion(strucDoc, ar.getRevision(), null);
          auditedRecord
              .getRecord()
              .setParents(
                  latestRecord.getParents()); // RSPAC-1304: export parents of latest revision
          versionsToExport.add(auditedRecord);
        }
      } else if (latestRecord.isMediaRecord()) {
        List<?> mediaHistory =
            auditManager.getRevisionsForEntity(latestRecord.getClass(), latestRecord.getId());
        for (Object o : mediaHistory) {
          AuditedEntity<EcatMediaFile> am = (AuditedEntity<EcatMediaFile>) o;
          am.getEntity().setParents(latestRecord.getParents());
          versionsToExport.add(new AuditedRecord(am.getEntity(), am.getRevision()));
        }
      }
    } else {
      if (latestRecord.isIdentifiedByOid(recordToExportOid)) {
        // recordToExportOid pointing to latest version (either no version specified, or doc version
        // that is latest)
        versionsToExport.add(new AuditedRecord(latestRecord, null));
      } else {
        // recordToExportOid points to older version of the record
        AuditedRecord auditedRecord = null;
        if (latestRecord.isMediaRecord()) {
          auditedRecord =
              auditManager.getMediaFileVersion(
                  (EcatMediaFile) latestRecord, recordToExportOid.getVersionId());
        } else if (latestRecord.isStructuredDocument()) {
          auditedRecord =
              auditManager.getDocumentRevisionOrVersion(
                  latestRecord.asStrucDoc(), null, recordToExportOid.getVersionId());
        }
        if (auditedRecord != null) {
          versionsToExport.add(auditedRecord);
        } else {
          log.warn("couldn't retrieve record version for " + recordToExportOid.getIdString());
        }
      }
    }
    return versionsToExport;
  }
}
