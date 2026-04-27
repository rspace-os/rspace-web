package com.researchspace.service.impl;

import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.permissions.ConstraintPermissionResolver;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.service.BaseRecordManager;
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
  private final BaseRecordManager baseRecordManager;
  private final SharingHandler recordSharingHandler;

  private final ConstraintPermissionResolver constraintPermissionResolver =
      new ConstraintPermissionResolver();

  @Autowired
  public TemplateTransferService(
      AuditTrailService auditTrailService,
      RecordGroupSharingDao recordGroupSharingDao,
      FolderManager folderManager,
      RecordManager recordManager,
      BaseRecordManager baseRecordManager,
      SharingHandler recordSharingHandler) {
    this.auditTrailService = auditTrailService;
    this.recordGroupSharingDao = recordGroupSharingDao;
    this.folderManager = folderManager;
    this.recordManager = recordManager;
    this.baseRecordManager = baseRecordManager;
    this.recordSharingHandler = recordSharingHandler;
  }

  public void transferOwnership(User originalOwner, User newOwner) {
    List<BaseRecord> sharedRecords = recordGroupSharingDao.getTemplatesSharedByUser(originalOwner);

    if (!sharedRecords.isEmpty()) {

      for (BaseRecord template : sharedRecords) {
        System.out.println("@@@ Unsharing: " + template.getName() + " (" + template.getId() + ")");
        List<RecordGroupSharing> sharings =
            recordGroupSharingDao.getRecordGroupSharingsForRecord(template.getId());
        System.out.println("@@@ Has this many sharings: " + sharings.size());
        for (RecordGroupSharing sharing : sharings) {
          System.out.println("@@@ About to share: " + sharing.getId());
          recordSharingHandler.unshare(sharing.getId(), originalOwner);
          System.out.println("@@@ Unshared sharing");
        }
        System.out.println("@@@ Unshared all");
      }

      System.out.println("@@@ This many shared templates: " + sharedRecords.size());
      System.out.println(
          "@@@ Transferring ownership of template from: "
              + originalOwner.getUsername()
              + " to: "
              + newOwner.getUsername());
      List<Long> templateIds =
          sharedRecords.stream().map(BaseRecord::getId).collect(Collectors.toList());

      Folder deletedUserFolder = determineDeletedTemplatesFolder(originalOwner, newOwner);
      System.out.println("@@@ Will move templates to folder with ID: " + deletedUserFolder.getId());

      // TODO:  Move the above into a separate method
      recordManager.moveUsersRecordsToFolder(templateIds, originalOwner, deletedUserFolder);

      String deletedUserName = originalOwner.getUsername() + DELETED_USER_NAME_SUFFIX;
      recordManager.transferTemplates(
          originalOwner, newOwner, templateIds, deletedUserFolder, deletedUserName);

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
    System.out.println("@@@ This many template folder subfolders: " + templateSubFolders.size());
    for (Folder subfolder : templateSubFolders) {
      if (DELETED_USER_TEMPLATES_FOLDER.equals(subfolder.getName())) {
        deletedUsersTemplates = subfolder;
      }
    }
    if (null == deletedUsersTemplates) {
      System.out.println("@@@ Creating new deleted user templates folder");
      deletedUsersTemplates =
          folderManager.createNewFolder(
              templateFolder.getId(), DELETED_USER_TEMPLATES_FOLDER, newOwner);
      System.out.println("@@@ Created new deleted user templates folder");
    }
    List<Folder> deletedSubFolders = folderManager.getSubFolders(deletedUsersTemplates);
    System.out.println("@@@ This many deleted subfolders: " + deletedSubFolders.size());
    Folder deletedUserFolder = null;
    for (Folder deletedSubFolder : deletedSubFolders) {
      if (originalOwner.getUsername().equals(deletedSubFolder.getName())) {
        deletedUserFolder = deletedSubFolder;
      }
    }
    if (null == deletedUserFolder) {
      System.out.println("@@@ Creating new deleted user folder");
      deletedUserFolder =
          folderManager.createNewFolder(
              deletedUsersTemplates.getId(), originalOwner.getUsername(), newOwner);
      System.out.println(
          "@@@ Created new deleted user templates folder: " + deletedUserFolder.getName());
    }

    return deletedUserFolder;
  }
}
