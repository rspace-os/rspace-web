package com.researchspace.webapp.controller;

import com.researchspace.core.util.CollectionFilter;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.BaseRecord;

/**
 * Filter that does not return any record that is
 *
 * <ul>
 *   <li>deleted
 *   <li>invisible
 *   <li>a group shared folder, a shared folder or a subfolder of either of these folders
 *   <li>a Notebook
 *   <li>Not a regular folder
 * </ul>
 *
 * Should be called within scope of a database transaction.
 */
public class FolderToDisplayInMoveDialogFilter implements CollectionFilter<BaseRecord> {

  @Override
  public boolean filter(BaseRecord br) {
    if (br == null) {
      return false;
    }
    return !(!br.isFolder()
        || br.isDeleted()
        || br.isInvisible()
        || br.hasType(RecordType.NOTEBOOK)
        || br.hasType(RecordType.SHARED_GROUP_FOLDER_ROOT)
        || isSharedFolder(br));
  }

  private boolean isSharedFolder(BaseRecord br) {
    return br.getParentHierarchyForUser(br.getOwner()).contains(br.getOwner().getSharedFolder());
  }
}
