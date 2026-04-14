package com.researchspace.service.impl;

import com.researchspace.dao.FolderDao;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.ConstraintPermissionResolver;
import com.researchspace.model.permissions.RecordSharingACL;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.TransferService;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TemplateTransferService implements TransferService {
  private final AuditTrailService auditTrailService;
  private final RecordGroupSharingDao recordGroupSharingDao;
  private final FolderDao folderDao;
  private final BaseRecordManager baseRecordManager;

  private final ConstraintPermissionResolver constraintPermissionResolver =
      new ConstraintPermissionResolver();

  @Autowired
  public TemplateTransferService(
      AuditTrailService auditTrailService,
      RecordGroupSharingDao recordGroupSharingDao,
      FolderDao folderDao,
      BaseRecordManager baseRecordManager) {
    this.auditTrailService = auditTrailService;
    this.recordGroupSharingDao = recordGroupSharingDao;
    this.folderDao = folderDao;
    this.baseRecordManager = baseRecordManager;
  }

  public void transferOwnership(User originalOwner, User newOwner) {
    List<BaseRecord> sharedRecords = recordGroupSharingDao.getTemplatesSharedByUser(originalOwner);

    for (BaseRecord template : sharedRecords) {
      RecordSharingACL tempAcl = template.getSharingACL();
      List<ACLElement> aclElements = tempAcl.getAclElements();
      System.out.println(
          "@@@ " + template.getName() + "has this many ACL elements: " + aclElements.size());
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

      Folder templateFolder = folderDao.getTemplateFolderForUser(newOwner);
      System.out.println("@@@ Will move templates to folder with ID: " + templateFolder.getId());

      recordGroupSharingDao.transferOwnershipOfTemplates(
          originalOwner, newOwner, templateIds, templateFolder);
      String description =
          String.format(
              "Ownership of template transferred from %s to %s",
              originalOwner.getUsername(), newOwner.getUsername());

      for (BaseRecord template : sharedRecords) {
        // Update the ACL to point at the new owner.
        // This is pretty horrible, but the current permission classes simply
        // don't allow us to change users nicely.
        RecordSharingACL templateACL = template.getSharingACL();
        String acl = templateACL.getAcl();
        System.out.println("@@@ ACL for template: " + template.getName() + " : " + acl);
        String newAcl = acl.replace(originalOwner.getUsername(), newOwner.getUsername());
        System.out.println("@@@ New ACL: " + newAcl);
        System.out.println("@@@ And ACL: " + acl);

        List<ACLElement> toAdd = new ArrayList<>();
        List<ACLElement> toRemove = new ArrayList<>();
        for (ACLElement aclElement : templateACL.getAclElements()) {
          System.out.println(
              "@@@ ACL element: "
                  + aclElement.getUserOrGrpUniqueName()
                  + " , "
                  + aclElement.getAsString());

          if (aclElement.getUserOrGrpUniqueName().equals(originalOwner.getUsername())) {
            toRemove.add(aclElement);
            ConstraintBasedPermission cbp = constraintPermissionResolver.resolvePermission(aclElement);
            ACLElement newElement = new ACLElement(newOwner.getUsername(), cbp);
            toAdd.add(newElement);
            System.out.println(
                "@@@ Replacing with element: "
                    + newElement.getUserOrGrpUniqueName()
                    + " , "
                    + newElement.getAsString());
          }
        }
        for (ACLElement remove : toRemove) {
          templateACL.removeACLElement(remove);
        }
        for (ACLElement add : toAdd) {
          templateACL.addACLElement(add);
        }
        baseRecordManager.save(template, newOwner);

        auditTrailService.notify(
            new GenericEvent(newOwner, template, AuditAction.TRANSFER, description));
      }
    }
  }
}
