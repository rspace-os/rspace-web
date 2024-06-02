package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.record.Folder;

/** Creates user folder structure for new users. */
public interface UserFolderCreator {
  String SHARED_SNIPPETS_FOLDER_PREFIX = "SNIPPETS_";

  /**
   * Creates a folder structure for new users
   *
   * @param subject
   * @param rootForUser
   * @return A POJO containing references to the created folders and documents
   */
  UserFolderSetup initStandardFolderStructure(User subject, Folder rootForUser);

  Folder createSharedSnippetFolder(User subject, Folder snippetFolder);
}
