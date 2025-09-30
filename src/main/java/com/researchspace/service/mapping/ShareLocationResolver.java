package com.researchspace.service.mapping;

import com.researchspace.dao.FolderDao;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSPath;
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

  /***
   * A record can be shared to multiple users or groups. This method finds the parent of the shared record that relates
   * to the user or group the record is shared to in the specific RecordGroupSharing.
   */
  public BaseRecord resolveLocation(RecordGroupSharing share, BaseRecord record) {
    // case of implicit share of a document within a notebook, rather than a notebook itself
    if (share.getShared().isNotebook() && !record.isNotebook()) {
      return share.getShared();
    }

    if (share.getSharee().isGroup()) {
      return findGroupSharedFolder(share);
    }

    return findUserToUserSharedFolder(share, record);
  }

  /***
   * Returns the path of the record within the shared folder context of the RecordGroupSharing.
   */
  public String resolvePath(RecordGroupSharing share, BaseRecord record) {
    Folder sharedRoot;
    if (share.getSharee().isGroup()) {
      sharedRoot = folderDao.getSharedFolderForGroup(share.getSharee().asGroup());
    } else {
      sharedRoot =
          folderDao.getIndividualSharedFolderForUsers(
              share.getSharedBy(), share.getSharee().asUser(), share.getShared());
    }

    if (sharedRoot == null) {
      return null;
    }

    BaseRecord parent = resolveLocation(share, record);
    if (parent == null) {
      return null;
    }

    RSPath path = parent.getShortestPathToParent(sharedRoot);
    return path == null ? null : path.getPathAsString("/");
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

  private static boolean isInUserToUserSharedFolder(RecordGroupSharing share, Folder folder) {
    return isDirectUserToUserSharedFolder(folder, share)
        || Optional.ofNullable(folder.getAllAncestors())
            .map(
                ancestors ->
                    ancestors.stream()
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
