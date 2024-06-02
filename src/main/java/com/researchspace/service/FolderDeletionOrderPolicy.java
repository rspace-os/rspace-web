package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.record.Folder;

/** Calculates the order in which items should be deleted from <code>toDelete</code> */
public interface FolderDeletionOrderPolicy {

  /**
   * No null args
   *
   * @param toDelete The top-level folder to delete, may contain nested fo
   * @param parent The parent of the item to delete - ie. its containing folder
   * @param subject The user performing the deletion
   * @return A {@link DeletionPlan} which a DeletionExecutor will use to perform the deletion
   * @throws IllegalArgumentException if <code>toDelete</code> is not valid to be deleted
   */
  DeletionPlan calculateDeletionOrder(Folder toDelete, Folder parent, User subject);
}
