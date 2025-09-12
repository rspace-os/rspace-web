package com.researchspace.service.mapping;

import com.researchspace.dao.FolderDao;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RecordToFolder;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ShareLocationResolver {
  private final FolderDao folderDao;

  @Autowired
  public ShareLocationResolver(FolderDao folderDao) {
    this.folderDao = folderDao;
  }

  public BaseRecord resolveLocation(RecordGroupSharing share, BaseRecord record) {
    if (share.getShared().isNotebook()) {
      return findParentNotebook(record);
    }

    if (share.getSharee().isGroup()) {
      return findGroupSharedFolder(share);
    }

    return findUserToUserSharedFolder(share, record);
  }

  BaseRecord findParentNotebook(BaseRecord record) {
    return record.getParents().stream()
        .map(RecordToFolder::getFolder)
        .filter(Folder::isNotebook)
        .findFirst()
        .orElse(null);
  }

  BaseRecord findGroupSharedFolder(RecordGroupSharing share) {
    try {
      Folder groupRoot = folderDao.getSharedFolderForGroup(share.getSharee().asGroup());
      if (groupRoot == null) {
        return firstSharedFolderParentOf(share.getShared());
      }
      return share.getShared().getParents().stream()
          .map(RecordToFolder::getFolder)
          .filter(
              folder ->
                  folder.equals(groupRoot)
                      || Optional.ofNullable(folder.getAllAncestors())
                          .map(ancestors -> ancestors.stream().anyMatch(a -> a.equals(groupRoot)))
                          .orElse(false))
          .map(f -> (BaseRecord) f)
          .findFirst()
          .orElse(firstSharedFolderParentOf(share.getShared()));
    } catch (RuntimeException ex) {
      // fall back to the first shared folder parent if DAO lookup fails
      return firstSharedFolderParentOf(share.getShared());
    }
  }

  BaseRecord firstSharedFolderParentOf(BaseRecord record) {
    return record.getParents().stream()
        .map(RecordToFolder::getFolder)
        .filter(Folder::isSharedFolder)
        .findFirst()
        .orElse(null);
  }

  BaseRecord findUserToUserSharedFolder(RecordGroupSharing share, BaseRecord record) {
    return record.getParents().stream()
        .map(RecordToFolder::getFolder)
        .filter(Folder::isSharedFolder)
        .filter(folder -> isDirectUserToUserSharedFolder(folder, share))
        .findFirst()
        .orElse(null);
  }

  static boolean isDirectUserToUserSharedFolder(Folder folder, RecordGroupSharing share) {
    String name = folder.getName();
    if (name == null || !name.contains("-")) {
      return false;
    }
    String[] parts = name.split("-", 2);
    if (parts.length != 2) {
      return false;
    }
    String sharerUsername = share.getSharedBy().getUsername();
    String recipientUsername = share.getSharee().asUser().getUsername();
    Set<String> users = Set.of(parts[0], parts[1]);
    return users.contains(sharerUsername) && users.contains(recipientUsername);
  }
}
