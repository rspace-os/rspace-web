package com.researchspace.testutils;

import com.researchspace.model.User;
import com.researchspace.service.FolderManager;
import com.researchspace.service.UserFolderSetup;
import com.researchspace.service.impl.MockFolderStructure;
import org.apache.commons.lang3.RandomUtils;

public class FolderTestUtils {

  /**
   * Creates a folder structure that is used when setting up user accounts; with ids set to random
   * values
   *
   * @param any
   * @return
   */
  public static UserFolderSetup createDefaultFolderStructure(User any) {
    return createDefaultFolderStructure(new MockFolderStructure(), any, null);
  }

  /** As createDefaultFolderStructure, with the caller keeping hold of the created structure */
  public static UserFolderSetup createDefaultFolderStructure(
      MockFolderStructure structure, User any, FolderManager fm) {
    UserFolderSetup setup = structure.create(any, fm);
    structure.getSavedFolders().forEach(folder -> folder.setId(RandomUtils.nextLong()));
    return setup;
  }
}
