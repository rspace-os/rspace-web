package com.researchspace.service.impl;

import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.views.CompositeRecordOperationResult;
import com.researchspace.service.DeletionExecutor;
import com.researchspace.service.DeletionPlan;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RecordSharingManager;
import java.util.Iterator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class DeleteFromSharedFolderExecutor implements DeletionExecutor {

  private @Autowired RecordSharingManager recShareManager;
  private @Autowired FolderManager folderMgr;

  @Override
  public void execute(CompositeRecordOperationResult result, DeletionPlan plan) {
    Validate.noNullElements(new Object[] {result, plan});
    BaseRecord topLevelParent = plan.getFinalElementToRemove();
    if (topLevelParent == null || !topLevelParent.isFolder()) {
      throw new IllegalStateException(
          "1st item in deletion stack must be a folder but was: " + topLevelParent == null
              ? "null"
              : topLevelParent.getType());
    }
    log.info("Deleting items from shared folder ");
    Folder topLevelFolder = (Folder) topLevelParent;
    Iterator<BaseRecord> itemsToDelete = plan.iterator();
    while (itemsToDelete.hasNext()) {
      BaseRecord toDelete = itemsToDelete.next();
      if (topLevelFolder.equals(toDelete)) {
        break; // this is a special case for the last element to delete
      }
      if (toDelete.isStructuredDocument() || toDelete.isNotebook()) {
        // this will delete a single item as well
        recShareManager.unshareFromSharedFolder(plan.getUser(), toDelete, plan.getPath());
      } else {
        Folder parent =
            toDelete.getShortestPathToParent(topLevelFolder).getImmediateParentOf(toDelete).get();
        if (!parent.isNotebook()) {
          folderMgr.removeBaseRecordFromFolder(toDelete, parent.getId());
        }
      }
      result.addRecord(toDelete);
    }
    folderMgr.removeBaseRecordFromFolder(topLevelFolder, plan.getParentOfDeletedItem().getId());
    result.addRecord(topLevelFolder);
    log.info("Deleted {} items ", result.getRecords().size());
  }
}
