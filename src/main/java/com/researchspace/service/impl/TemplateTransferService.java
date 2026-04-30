package com.researchspace.service.impl;

import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.SharingHandler;
import com.researchspace.service.TransferService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TemplateTransferService implements TransferService {

  public static final String DELETED_USER_TEMPLATES_FOLDER = "Deleted Users";
  public static final String DELETED_USER_NAME_SUFFIX = "(Deleted)";

  private final AuditTrailService auditTrailService;
  private final RecordGroupSharingDao recordGroupSharingDao;
  private final FolderManager folderManager;
  private final RecordManager recordManager;
  private final SharingHandler recordSharingHandler;

  @Autowired
  public TemplateTransferService(
      AuditTrailService auditTrailService,
      RecordGroupSharingDao recordGroupSharingDao,
      FolderManager folderManager,
      RecordManager recordManager,
      SharingHandler recordSharingHandler) {
    this.auditTrailService = auditTrailService;
    this.recordGroupSharingDao = recordGroupSharingDao;
    this.folderManager = folderManager;
    this.recordManager = recordManager;
    this.recordSharingHandler = recordSharingHandler;
  }

  public void transferOwnership(User originalOwner, User newOwner) {
    List<BaseRecord> sharedRecords = recordGroupSharingDao.getTemplatesSharedByUser(originalOwner);

    if (!sharedRecords.isEmpty()) {

      for (BaseRecord template : sharedRecords) {
        List<RecordGroupSharing> sharings =
            recordGroupSharingDao.getRecordGroupSharingsForRecord(template.getId());
        for (RecordGroupSharing sharing : sharings) {
          recordSharingHandler.unshare(sharing.getId(), originalOwner);
        }
      }

      List<Long> templateIds =
          sharedRecords.stream().map(BaseRecord::getId).collect(Collectors.toList());

      Folder deletedUserFolder = determineDeletedTemplatesFolder(originalOwner, newOwner);

      recordManager.moveUsersRecordsToFolder(templateIds, originalOwner, deletedUserFolder);

      String deletedUserName = originalOwner.getUsername() + DELETED_USER_NAME_SUFFIX;
      recordManager.transferTemplates(originalOwner, newOwner, templateIds, deletedUserName);

      String description =
          String.format(
              "Ownership of template transferred from %s to %s",
              originalOwner.getUsername(), newOwner.getUsername());

      for (BaseRecord template : sharedRecords) {
        auditTrailService.notify(
            new GenericEvent(newOwner, template, AuditAction.TRANSFER, description));
      }
    }
  }

  private Folder determineDeletedTemplatesFolder(User originalOwner, User newOwner) {
    Folder templateFolder = folderManager.getTemplateFolderForUser(newOwner);
    Folder deletedUsersTemplates = null;
    List<Folder> templateSubFolders = folderManager.getSubFolders(templateFolder);
    for (Folder subfolder : templateSubFolders) {
      if (DELETED_USER_TEMPLATES_FOLDER.equals(subfolder.getName())) {
        deletedUsersTemplates = subfolder;
      }
    }
    if (null == deletedUsersTemplates) {
      deletedUsersTemplates =
          folderManager.createNewFolder(
              templateFolder.getId(), DELETED_USER_TEMPLATES_FOLDER, newOwner);
    }
    List<Folder> deletedSubFolders = folderManager.getSubFolders(deletedUsersTemplates);
    Folder deletedUserFolder = null;
    for (Folder deletedSubFolder : deletedSubFolders) {
      if (originalOwner.getUsername().equals(deletedSubFolder.getName())) {
        deletedUserFolder = deletedSubFolder;
      }
    }
    if (null == deletedUserFolder) {
      deletedUserFolder =
          folderManager.createNewFolder(
              deletedUsersTemplates.getId(), originalOwner.getUsername(), newOwner);
    }

    return deletedUserFolder;
  }
}
