package com.researchspace.webapp.controller;

import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import java.util.Collection;
import org.springframework.ui.Model;

public interface IWorkspacePermissionsDTOBuilder {

  /**
   * Calculates permissions for actions based on the supplied folder for return to the UI.
   *
   * @param parentFolder
   * @param usr
   * @param model
   * @param records
   * @param previousFolderId
   * @param isSearch whether the records is obtained from Search or Folder naviation
   * @return
   */
  ActionPermissionsDTO addCreateAndOptionsMenuPermissions(
      Folder parentFolder,
      User usr,
      Model model,
      Collection<? extends BaseRecord> records,
      Long previousFolderId,
      boolean isSearch);
}
