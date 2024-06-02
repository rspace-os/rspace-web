package com.researchspace.service.impl;

import static com.researchspace.service.UserFolderCreator.SHARED_SNIPPETS_FOLDER_PREFIX;

import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.service.RecordManager;
import com.researchspace.service.UserFolderCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

// Use this to update all users with new content, for an EXISTING RSpace. There
// should ALSO be code that does the creation of this new content in DefaultUserFolderCreator.
// This class should just check whether the code in DefaultUserFolderCreator needs to be run
// NOTE - this class is called in the postlogin hook.
@Transactional
public class UserContentUpdaterImpl implements UserContentUpdater {
  @Autowired
  @Qualifier("defaultUserFolderCreator")
  private UserFolderCreator userFolderCreator;

  @Autowired private RecordManager recordManager;

  @Override // see comments in the Interface and the class comments above
  public void doUserContentUpdates(User subject) {
    createSharedSnippetFolder(subject);
  }

  private void createSharedSnippetFolder(User subject) {
    Folder snippetFolder =
        recordManager.getGallerySubFolderForUser(Folder.SNIPPETS_FOLDER, subject);
    if (snippetFolder.getSubFolderByName(SHARED_SNIPPETS_FOLDER_PREFIX + Folder.SHARED_FOLDER_NAME)
        == null) {
      userFolderCreator.createSharedSnippetFolder(subject, snippetFolder);
    }
  }
}
