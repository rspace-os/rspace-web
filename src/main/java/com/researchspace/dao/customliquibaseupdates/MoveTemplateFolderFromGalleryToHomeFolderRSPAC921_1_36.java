package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.model.User;
import com.researchspace.model.permissions.PermissionFactory;
import com.researchspace.model.record.ACLPropagationPolicy;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordContainerProcessor;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RecordManager;

public class MoveTemplateFolderFromGalleryToHomeFolderRSPAC921_1_36
    extends AbstractUpdateRequiringUserPermissions {

  private FolderManager folderMgr;
  private RecordManager recMgr;

  private PermissionFactory permFactory;

  @Override
  protected void addBeans() {
    super.addBeans();
    folderMgr = context.getBean(FolderManager.class);
    recMgr = context.getBean(RecordManager.class);
    permFactory = context.getBean(PermissionFactory.class);
  }

  @Override
  public String getConfirmationMessage() {
    return "Template folders migrated";
  }

  protected boolean doUserSpecificAction(User user) {
    logger.info("Transferring template folder for : {}", user.getUsername());
    Folder templateFolder = folderMgr.getTemplateFolderForUser(user);
    Folder root = folderMgr.getRootFolderForUser(user);
    Folder media = folderMgr.getGalleryRootFolderForUser(user);
    if (root == null) {
      logger.warn(
          "Root folder for {}  was null, perhaps user has never logged in yet?",
          user.getUsername());
      return false;
    }

    if (!templateFolder.getParent().equals(media)) {
      logger.warn(
          "Template folder is not in media folder but is in {} (id={}), perhaps is already moved?"
              + " Skipping to next user",
          templateFolder.getParent().getName(),
          templateFolder.getParent().getId());
      return false;
    }
    folderMgr.move(templateFolder.getId(), root.getId(), media.getId(), user);
    folderMgr.save(templateFolder, user);
    templateFolder.clearACL(true);
    permFactory.setUpACLForIndividualTemplateFolder(templateFolder, user);
    ACLPropagationPolicy.DEFAULT_POLICY.propagate(templateFolder);

    TemplateModifier modifier = new TemplateModifier();
    for (BaseRecord child : templateFolder.getChildrens()) {
      if (child.isFolder()) {
        ((Folder) child).process(modifier);
      } else {
        modifier.modifyProperties(child);
        recMgr.save((Record) child, child.getOwner());
      }
    }
    logger.warn("Transferring template folder successful for {}", user.getUsername());
    return true;
  }

  class TemplateModifier implements RecordContainerProcessor {

    @Override
    public boolean process(BaseRecord rc) {

      modifyProperties(rc);
      if (rc.isFolder()) {
        folderMgr.save((Folder) rc, rc.getOwner());
      } else {
        recMgr.save((Record) rc, rc.getOwner());
      }
      return true;
    }

    void modifyProperties(BaseRecord rc) {
      permFactory.setUpACLForTemplateFolderChildPermissions(rc, rc.getOwner());
    }
  }
}
