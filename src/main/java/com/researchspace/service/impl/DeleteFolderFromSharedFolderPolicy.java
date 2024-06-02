package com.researchspace.service.impl;

import static com.researchspace.service.RecordDeletionManager.MIN_PATH_LENGTH_TOSHARED_ROOT_FOLDER;

import com.researchspace.dao.FolderDao;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSPath;
import com.researchspace.service.DeletionPlan;
import com.researchspace.service.FolderDeletionOrderPolicy;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;

public class DeleteFolderFromSharedFolderPolicy implements FolderDeletionOrderPolicy {

  private @Autowired FolderDao folderDao;

  /**
   * @throws IllegalArgumentException if <code>toDelete</code> is not in shared folder tree.
   * @see
   *     com.researchspace.service.FolderDeletionOrderPolicy#calculateDeletionOrder(com.researchspace.model.record.Folder,
   *     com.researchspace.model.record.Folder, com.researchspace.model.User)
   */
  @Override
  public DeletionPlan calculateDeletionOrder(Folder toDelete, Folder parent, User subject) {
    Folder sharedFolderRoot = folderDao.getUserSharedFolder(subject);
    RSPath path = parent.getShortestPathToParent(sharedFolderRoot);
    if (path.isEmpty() || path.size() <= MIN_PATH_LENGTH_TOSHARED_ROOT_FOLDER) {
      String msg =
          String.format(
              "The folder '%s' to be deleted - id [%d] is not in a shared folder!",
              toDelete.getName(), toDelete.getId());
      throw new IllegalArgumentException(msg);
    }
    DeletionPlan plan = new DeletionPlan(subject, path, parent);
    plan.add(toDelete);
    scanFolderForItems(plan, toDelete.getChildrens());
    return plan;
  }

  private void scanFolderForItems(DeletionPlan plan, Set<BaseRecord> childrens) {
    for (BaseRecord br : childrens) {
      plan.add(br);
      // we don't want to add notebook items to be deleted - these would
      // be deleted from original notebook!
      if (br.isFolder() && !br.isNotebook()) {
        scanFolderForItems(plan, br.getChildrens());
      }
    }
  }
}
