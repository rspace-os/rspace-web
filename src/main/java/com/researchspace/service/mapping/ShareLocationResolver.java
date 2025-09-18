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
      // If the shared entity is a notebook, that notebook is the intended location regardless of
      // how many notebooks the document belongs to.
      return share.getShared();
    }

    if (share.getSharee().isGroup()) {
      return findGroupSharedFolder(share);
    }

    return findUserToUserSharedFolder(share, record);
  }

  private BaseRecord findGroupSharedFolder(RecordGroupSharing share) {
      Folder groupRoot = folderDao.getSharedFolderForGroup(share.getSharee().asGroup());
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
          .orElse(null);
  }

  private BaseRecord findUserToUserSharedFolder(RecordGroupSharing share, BaseRecord record) {
    return record.getParents().stream()
        .map(RecordToFolder::getFolder)
        .filter(Folder::isSharedFolder)
        .filter(folder -> isInUserToUserSharedFolder(share, folder))
        .map(f -> (BaseRecord) f)
        .findFirst()
        .orElse(null);
  }

  /**
   * Checks if the record is within the user-to-user shared folder structure.
   */
  private static boolean isInUserToUserSharedFolder(RecordGroupSharing share, Folder folder) {
    return isDirectUserToUserSharedFolder(folder, share)
            || Optional.ofNullable(folder.getAllAncestors())
            .map(ancestors -> ancestors.stream()
                    .filter(Folder::isSharedFolder)
                    .anyMatch(ancestor -> isDirectUserToUserSharedFolder(ancestor, share)))
            .orElse(false);
  }

  private static boolean isDirectUserToUserSharedFolder(Folder folder, RecordGroupSharing share) {
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
