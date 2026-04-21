package com.researchspace.service.impl;

import com.researchspace.dao.FolderDao;
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
import com.researchspace.service.SharingHandler;
import com.researchspace.service.TransferService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TemplateTransferService implements TransferService {

  public final String DELETED_USER_TEMPLATES_FOLDER = "Deleted Users";

  private final AuditTrailService auditTrailService;
  private final RecordGroupSharingDao recordGroupSharingDao;
  private final FolderDao folderDao;
  private final FolderManager folderManager;
  private final BaseRecordManager baseRecordManager;
  private final SharingHandler recordSharingHandler;

  private final ConstraintPermissionResolver constraintPermissionResolver =
      new ConstraintPermissionResolver();

  @Autowired
  public TemplateTransferService(
      AuditTrailService auditTrailService,
      RecordGroupSharingDao recordGroupSharingDao,
      FolderDao folderDao,
      FolderManager folderManager,
      BaseRecordManager baseRecordManager,
      SharingHandler recordSharingHandler) {
    this.auditTrailService = auditTrailService;
    this.recordGroupSharingDao = recordGroupSharingDao;
    this.folderDao = folderDao;
    this.folderManager = folderManager;
    this.baseRecordManager = baseRecordManager;
    this.recordSharingHandler = recordSharingHandler;
  }

  public void transferOwnership(User originalOwner, User newOwner) {
    List<BaseRecord> sharedRecords = recordGroupSharingDao.getTemplatesSharedByUser(originalOwner);

    for (BaseRecord template : sharedRecords) {
      //      RecordSharingACL tempAcl = template.getSharingACL();
      //      List<ACLElement> aclElements = tempAcl.getAclElements();
      //      System.out.println(
      //          "@@@ " + template.getName() + "has this many ACL elements: " +
      // aclElements.size());
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

    if (!sharedRecords.isEmpty()) {
      // transferOwnershipOfForms(originalOwner, newOwner, formIds);
      System.out.println("@@@ This many shared templates: " + sharedRecords.size());
      System.out.println(
          "@@@ Transferring ownership of template from: "
              + originalOwner.getUsername()
              + " to: "
              + newOwner.getUsername());
      List<Long> templateIds =
          sharedRecords.stream().map(BaseRecord::getId).collect(Collectors.toList());

      // Folder templateFolder = folderDao.getTemplateFolderForUser(newOwner);
      Folder templateFolder = folderManager.getTemplateFolderForUser(newOwner);
      //      List<Long> templateChildren = folderManager.getFolderChildrenIds(templateFolder);
      //      System.out.println("@@@ Template folder children: " + templateChildren);
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
      //      List<Long> deletedTemplatesChildrenIds =
      //          folderManager.getFolderChildrenIds(deletedUsersTemplates);
      //      System.out.println("@@@ Deleted templates children: " + deletedTemplatesChildrenIds);
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

      System.out.println("@@@ Will move templates to folder with ID: " + deletedUserFolder.getId());

      recordGroupSharingDao.transferOwnershipOfTemplates(
          originalOwner, newOwner, templateIds, deletedUserFolder);
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

  //  @Override
  //  public void updateACLs(User originalOwner, User newOwner) {
  //    List<BaseRecord> sharedRecords = recordGroupSharingDao.getTemplatesSharedByUser(newOwner);
  //
  //    for (BaseRecord template : sharedRecords) {
  //      // Update the ACL to point at the new owner.
  //      RecordSharingACL templateACL = template.getSharingACL();
  //      List<ACLElement> toAdd = new ArrayList<>();
  //      List<ACLElement> toRemove = new ArrayList<>();
  //      for (ACLElement aclElement : templateACL.getAclElements()) {
  //        System.out.println(
  //            "@@@ ACL element: "
  //                + aclElement.getUserOrGrpUniqueName()
  //                + " , "
  //                + aclElement.getAsString());
  //
  //        if (aclElement.getUserOrGrpUniqueName().equals(originalOwner.getUsername())) {
  //          toRemove.add(aclElement);
  //          ConstraintBasedPermission cbp =
  //              constraintPermissionResolver.resolvePermission(aclElement);
  //          ACLElement newElement = new ACLElement(newOwner.getUsername(), cbp);
  //          toAdd.add(newElement);
  //          System.out.println(
  //              "@@@ Replacing with element: "
  //                  + newElement.getUserOrGrpUniqueName()
  //                  + " , "
  //                  + newElement.getAsString());
  //        }
  //      }
  //      System.out.println("@@@ Scooby doo!");
  //      for (ACLElement remove : toRemove) {
  //        templateACL.removeACLElement(remove);
  //      }
  //      for (ACLElement add : toAdd) {
  //        templateACL.addACLElement(add);
  //      }
  //
  //      auditTrailService.notify(
  //          new GenericEvent(
  //              newOwner, template, AuditAction.TRANSFER, "Transferring ACLs UPDATE THIS"));
  //    }
  //  }
}
