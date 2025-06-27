package com.researchspace.webapp.controller;

import static com.researchspace.model.core.RecordType.INDIVIDUAL_SHARED_FOLDER_ROOT;
import static com.researchspace.model.core.RecordType.SHARED_FOLDER;
import static com.researchspace.model.core.RecordType.SHARED_GROUP_FOLDER_ROOT;
import static com.researchspace.model.core.RecordType.TEMPLATE;

import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RecordManager;
import java.util.Collection;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

/*
 * Helper class for workspace controller to create a DTO object to pass to the UI with permissions info.
 */
@Service
public class WorkspacePermissionsDTOBuilder implements IWorkspacePermissionsDTOBuilder {

  private FolderManager fMger;
  private RecordManager recMgr;

  @Getter @Setter private @Autowired IPermissionUtils permissionUtils;

  @Autowired
  public void setRecMgr(RecordManager recMgr) {
    this.recMgr = recMgr;
  }

  @Autowired
  public void setFolderMger(FolderManager fMger) {
    this.fMger = fMger;
  }

  public ActionPermissionsDTO addCreateAndOptionsMenuPermissions(
      Folder parentFolder,
      User usr,
      Model model,
      Collection<? extends BaseRecord> records,
      Long previousFolderId,
      boolean isSearch) {

    boolean createRecord =
        (parentFolder.isSharedFolder() && !parentFolder.hasType(INDIVIDUAL_SHARED_FOLDER_ROOT))
            || parentFolder.getSharingACL().isPermitted(usr, PermissionType.CREATE);
    boolean createFolder =
        parentFolder.getSharingACL().isPermitted(usr, PermissionType.CREATE_FOLDER);

    boolean isParentFolderInSharedTree =
        parentFolder.hasAncestorOfType(INDIVIDUAL_SHARED_FOLDER_ROOT, true)
            || parentFolder.hasAncestorOfType(SHARED_GROUP_FOLDER_ROOT, true);
    ActionPermissionsDTO dto = new ActionPermissionsDTO();
    if (records != null) {
      // allowed options per record in page.
      for (BaseRecord br : records) {
        boolean rename = calculateRenameEnabled(usr, br);
        boolean delete = calculateDeleteEnabled(usr, br);
        if (isSearch) {
          delete = delete && usr.isOwnerOfRecord(br);
        }
        // can't copy into shared folder, since doesn't then belong in owner's home folder
        boolean copy = calculateCopyEnabled(usr, isParentFolderInSharedTree, br, parentFolder);
        boolean export = calculateExportEnabled(br);
        boolean move =
            !br.isMediaRecord()
                && !br.isSnippet()
                && recMgr.canMove(
                    br, parentFolder, usr); // manager transaction as may need to query db
        if (isSearch) {
          move = move && usr.isOwnerOfRecord(br);
        }
        dto.setPermissionForRecord(br.getId(), PermissionType.DELETE.name(), delete);
        dto.setPermissionForRecord(br.getId(), PermissionType.RENAME.name(), rename);
        dto.setPermissionForRecord(br.getId(), PermissionType.COPY.name(), copy);
        dto.setPermissionForRecord(br.getId(), PermissionType.EXPORT.name(), export);

        // currently these use perms inherited from parent folder.
        dto.setPermissionForRecord(br.getId(), PermissionType.SEND.name(), move);

        if (br.isNotebookEntry()
            && parentFolder.isNotebook()
            && !br.getOwner().equals(parentFolder.getOwner())) {
          // can't move a notebook entry from notebook
          dto.setPermissionForRecord(br.getId(), PermissionType.SEND.name(), false);
        }
      }
    }
    // global permissions for the page.
    dto.setCreateRecord(createRecord);
    dto.setCreateFolder(createFolder);

    // override if it is notebook
    if (parentFolder.isNotebook()) {
      createRecord =
          createRecord
              || (isParentFolderInSharedTree
                  && permissionUtils.isPermitted(parentFolder, PermissionType.WRITE, usr));
      dto.setCreateRecord(createRecord);
      dto.setCreateFolder(false);
    }

    String movetargetRoot = "/"; // home folder by default
    if (parentFolder.hasType(SHARED_GROUP_FOLDER_ROOT)) {
      movetargetRoot = parentFolder.getId() + "";
    } else if (parentFolder.hasType(SHARED_FOLDER) && !parentFolder.isNotebook()) {
      Folder grpSharedFolder =
          fMger
              .getGroupOrIndividualShrdFolderRootFromSharedSubfolder(parentFolder.getId(), usr)
              .get();
      movetargetRoot = grpSharedFolder.getId() + "";
    } else if (parentFolder.hasType(SHARED_FOLDER)
        && parentFolder.isNotebook()
        && previousFolderId != null) {
      // this seems redundant, we only use the ID, which we already have.
      Folder previousFolder = fMger.getFolder(previousFolderId, usr);
      // this will be a shared folder above the notebook
      Folder grpSharedFolder =
          fMger
              .getGroupOrIndividualShrdFolderRootFromSharedSubfolder(previousFolder.getId(), usr)
              .get();
      movetargetRoot = grpSharedFolder.getId() + "";
    } else if (parentFolder.hasAncestorOfType(TEMPLATE, true)) {
      movetargetRoot = fMger.getTemplateFolderForUser(usr).getId() + "";
    }

    model.addAttribute("movetargetRoot", movetargetRoot);
    model.addAttribute("isNotebook", parentFolder.isNotebook());
    boolean canCreateEntry =
        parentFolder.isNotebook()
            && permissionUtils.isPermitted(parentFolder, PermissionType.CREATE, usr);
    model.addAttribute("allowCreateNewEntryInNotebook", canCreateEntry);

    model.addAttribute("createPermission", dto);
    model.addAttribute("allowThirdPartyImport", !parentFolder.isSharedFolder());
    model.addAttribute("allowCreateForm", !parentFolder.isSharedFolder());

    return dto;
  }

  private boolean calculateRenameEnabled(User usr, BaseRecord br) {
    return br.getSharingACL().isPermitted(usr, PermissionType.RENAME)
        && !br.hasType(SHARED_GROUP_FOLDER_ROOT); // rspac-1636
  }

  private boolean calculateDeleteEnabled(User usr, BaseRecord br) {
    return br.getSharingACL().isPermitted(usr, PermissionType.DELETE)
        && !br.hasType(SHARED_GROUP_FOLDER_ROOT); // rspac-1636
  }

  private boolean calculateCopyEnabled(
      User usr, boolean isParentFolderInSharedTree, BaseRecord br, Folder parentFolder) {
    if (br.isMediaRecord()
        || br.isSnippet()
        || !br.getSharingACL().isPermitted(usr, PermissionType.COPY)) {
      return false;
    }
    // rspac-940
    if (parentFolder.isNotebook() && isParentFolderInSharedTree) {
      return usr.equals(br.getOwner());
    } else if (isParentFolderInSharedTree) {
      return false;
    }
    return true;
  }

  private boolean calculateExportEnabled(BaseRecord br) {
    if (br.isSnippet()) { // || br.isMediaRecord()
      return false; // RSPAC-999
    }
    if (br.isFolder() && ((Folder) br).isTopLevelSharedFolder()) {
      return false; // RSPAC-1309
    }
    return true;
  }
}
