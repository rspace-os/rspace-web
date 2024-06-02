package com.researchspace.testutils;

import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.service.FolderManager;
import com.researchspace.service.UserFolderSetup;
import com.researchspace.service.impl.MockFolderStructure;
import org.apache.commons.lang.math.RandomUtils;

public class FolderTestUtils {

  /**
   * Creates a folder structure that is used when setting up user accounts; with ids set to random
   * values
   *
   * @param any
   * @return
   */
  public static UserFolderSetup createDefaultFolderStructure(User any) {
    UserFolderSetup setup = new MockFolderStructure().create(any);
    setup
        .getUserRoot()
        .process(
            br -> {
              br.setId(RandomUtils.nextLong());
              return true;
            });
    return setup;
  }

  public static UserFolderSetup createDefaultFolderStructure(
      User any, FolderManager fm, Folder folder) {
    UserFolderSetup setup = new MockFolderStructure().create(any, fm, folder);
    setup
        .getUserRoot()
        .process(
            br -> {
              br.setId(RandomUtils.nextLong());
              return true;
            });
    return setup;
  }
}
