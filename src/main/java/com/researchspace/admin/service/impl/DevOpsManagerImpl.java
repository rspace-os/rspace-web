package com.researchspace.admin.service.impl;

import com.researchspace.admin.service.DevOpsManager;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.FolderManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("devOpsManager")
public class DevOpsManagerImpl implements DevOpsManager {

  @Autowired private FolderManager folderMgr;

  @Autowired private BaseRecordManager baseRecordMgr;

  @Autowired private RecordGroupSharingDao recordGroupSharingDao;

  @Override
  public String fixRecord(GlobalIdentifier oid, User subject, boolean runFix) {
    String result;
    if (GlobalIdPrefix.FL.equals(oid.getPrefix())) {
      result = checkFolderFixes(oid, subject, runFix);
    } else {
      result =
          String.format(
              "No checks nor fixes currently implemented for records of type '%s'.",
              oid.getPrefix());
    }
    return result;
  }

  private String checkFolderFixes(GlobalIdentifier oid, User subject, boolean runFix) {
    Folder folder = folderMgr.getFolder(oid.getDbId(), subject);
    String result =
        String.format("Found a folder '%s' (type: %s).<br/>\n", folder.getName(), folder.getType());
    if (isSharedFolderMovedIntoWorkspace(folder)) {
      result +=
          "This is a shared folder, but is outside of 'Shared' hierarchy, which seems"
              + " wrong.<br/>\n";
      result += runSharedFolderMovedIntoWorkspaceFix(runFix, folder);
    } else {
      result += "It seems fine.<br/>\n";
    }
    return result;
  }

  private boolean isSharedFolderMovedIntoWorkspace(Folder folder) {
    return folder.hasType(RecordType.SHARED_FOLDER) && !folder.getParent().isSharedFolder();
  }

  private String runSharedFolderMovedIntoWorkspaceFix(boolean runFix, Folder folder) {
    String result = "";

    /* check if user who now has the shared folder on their Workspace has access to any better location */
    User parentFolderOwner = folder.getParent().getOwner();
    List<Folder> correctTargetFolders =
        findAcceptableTargetFoldersForUser(parentFolderOwner, folder);

    /* as a precaution, automatic fix will run only if there is exactly one possible move target */
    Folder correctTarget = null;
    if (correctTargetFolders.isEmpty()) {
      result += "Couldn't find any good new location for the folder.<br/>\n";
    } else if (correctTargetFolders.size() > 1) {
      result += "Seems like there is more than one possible group to move the folder back.<br/>\n";
    } else {
      correctTarget = correctTargetFolders.get(0);
      result +=
          String.format(
              "Found one possible target for moving the folder: '%s' (%s).<br/>\n",
              correctTarget.getName(), correctTarget.getGlobalIdentifier());
    }

    if (correctTarget != null) {
      if (runFix) {
        result += "Attempting to apply the fix... ";
        ServiceOperationResult<Folder> moveResult =
            folderMgr.move(
                folder.getId(),
                correctTarget.getId(),
                folder.getParent().getId(),
                parentFolderOwner);
        if (moveResult.isSucceeded()) {
          result += "the folder moved successfully.<br/>\n";
        } else {
          result += "move attempt was unsuccessful, for unknown reason.<br/>\n";
        }
      } else {
        result +=
            String.format(
                "To move current folder %s into folder %s call the current URL "
                    + "with '?update=true' suffix.<br/>\n",
                folder.getGlobalIdentifier(), correctTarget.getGlobalIdentifier());
      }
    }
    return result;
  }

  private List<Folder> findAcceptableTargetFoldersForUser(User user, Folder folder) {
    List<Folder> results = new ArrayList<>();

    /* for each of the user's groups, check if group's folder is a good location */
    Set<Group> ownerGroups = user.getGroups();
    for (Group currentGroup : ownerGroups) {
      List<BaseRecord> recordsSharedWithGroup =
          recordGroupSharingDao.getRecordsSharedByGroup(currentGroup.getId());
      if (isSharedFolderOrRecordSharedWithGroup(folder, recordsSharedWithGroup, user)) {
        results.add(folderMgr.getFolder(currentGroup.getCommunalGroupFolderId(), user));
      }
    }
    return results;
  }

  private boolean isSharedFolderOrRecordSharedWithGroup(
      BaseRecord recordToCheck, List<BaseRecord> recordsSharedWithGroup, User user) {
    if (recordToCheck.isFolder() && !recordToCheck.isNotebook()) {
      // for folders, confirm it's a shared folder & trigger the check for subfolders
      Folder folderToCheck = (Folder) recordToCheck;
      if (!folderToCheck.isSharedFolder()) {
        return false;
      }
      List<Long> childrenIds = folderMgr.getRecordIds(folderToCheck);
      for (Long childId : childrenIds) {
        BaseRecord child = baseRecordMgr.get(childId, user);
        if (!isSharedFolderOrRecordSharedWithGroup(child, recordsSharedWithGroup, user)) {
          return false;
        }
      }
    } else {
      // for record/notebook, confirm it's among records shared with group
      if (!recordsSharedWithGroup.contains(recordToCheck)) {
        return false;
      }
    }
    return true;
  }
}
