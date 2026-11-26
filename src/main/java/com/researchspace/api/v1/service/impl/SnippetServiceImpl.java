package com.researchspace.api.v1.service.impl;

import com.researchspace.api.v1.service.SnippetService;
import com.researchspace.auth.PermissionUtils;
import com.researchspace.model.User;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.record.Snippet;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RecordManager;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SnippetServiceImpl implements SnippetService {

  private final RecordManager recordManager;
  private final FolderManager folderManager;
  private final PermissionUtils permissionUtils;

  public SnippetServiceImpl(
      RecordManager recordManager, FolderManager folderManager, PermissionUtils permissionUtils) {
    this.recordManager = recordManager;
    this.folderManager = folderManager;
    this.permissionUtils = permissionUtils;
  }

  @Override
  public Snippet getSnippet(long id, User user) {
    Snippet snippet = recordManager.getAsSubclass(id, Snippet.class);
    permissionUtils.assertIsPermitted(snippet, PermissionType.READ, user, "read snippet");
    return snippet;
  }

  @Override
  public Optional<Folder> getGalleryParentFolder(Snippet snippet, User user) {
    Optional<Folder> ownerParent = snippet.getOwnerParent();
    if (snippet.getOwner().equals(user) && ownerParent.isPresent()) {
      return ownerParent;
    }
    Folder galleryRoot = folderManager.getGalleryRootFolderForUser(user);
    return snippet.getParents().stream()
        .filter(relation -> !relation.isRecordInFolderDeleted())
        .map(RecordToFolder::getFolder)
        .filter(
            folder -> !folder.isDeleted() && !folder.getShortestPathToParent(galleryRoot).isEmpty())
        .findFirst()
        .or(() -> ownerParent)
        .filter(folder -> permissionUtils.isPermitted(folder, PermissionType.READ, user));
  }
}
