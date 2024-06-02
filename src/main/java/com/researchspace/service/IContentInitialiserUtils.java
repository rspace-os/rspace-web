package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.record.ACLPropagationPolicy;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;

/**
 * Methods to perform aspects of user content initialisation that can be compsed and reused in
 * different {@link IContentInitializer}s
 */
public interface IContentInitialiserUtils {
  /**
   * Sets up user's root folder, initialises permissions.
   *
   * @param user
   * @return the root folder
   */
  Folder setupRootFolder(User user);

  public Folder addChild(Folder existingFolder, BaseRecord newTransientChild, User owner);

  public void delayForUniqueCreationTime();

  Folder addChild(
      Folder f, BaseRecord newTransientChild, User owner, ACLPropagationPolicy aclpolicy)
      throws IllegalAddChildOperation;
}
