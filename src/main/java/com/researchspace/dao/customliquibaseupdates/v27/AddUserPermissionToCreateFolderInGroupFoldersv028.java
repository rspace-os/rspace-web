package com.researchspace.dao.customliquibaseupdates.v27;

import com.researchspace.dao.FolderDao;
import com.researchspace.dao.customliquibaseupdates.AbstractCustomLiquibaseUpdater;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import java.util.List;
import liquibase.database.Database;
import org.hibernate.Session;

/** Adds FOLDER_CREATE permission to users in LabFolders */
public class AddUserPermissionToCreateFolderInGroupFoldersv028
    extends AbstractCustomLiquibaseUpdater {
  private FolderDao fDao;

  @Override
  protected void addBeans() {
    fDao = context.getBean("folderDao", FolderDao.class);
  }

  @Override
  public String getConfirmationMessage() {
    return "Updating  create folder in Shared Folder permissions complete";
  }

  @Override
  protected void doExecute(Database database) {
    Session s = sessionFactory.getCurrentSession();
    List<Group> gps =
        s.createQuery(" from Group  where groupType=:grouptype")
            .setParameter("grouptype", GroupType.LAB_GROUP)
            .list();
    for (Group grp : gps) {
      if (grp.getCommunalGroupFolderId() == null) {
        logger.warn("Group {} does not have a shared folder!", grp.getUniqueName());
        continue;
      }
      Folder grpFolder = fDao.get(grp.getCommunalGroupFolderId());
      ConstraintBasedPermission cbp =
          new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.CREATE_FOLDER);
      ACLElement acl = new ACLElement(grp.getUniqueName(), cbp);

      logger.info(
          "adding element {} to grp {} and folder {}",
          acl,
          grp.getDisplayName(),
          grpFolder.getName());
      propagateChange(grpFolder, acl);
      fDao.save(grpFolder);
    }
  }

  private void propagateChange(BaseRecord grpFolder, ACLElement acl) {
    grpFolder.getSharingACL().addACLElement(acl);
    for (BaseRecord child : grpFolder.getChildrens()) {
      propagateChange(child, acl);
    }
  }
}
