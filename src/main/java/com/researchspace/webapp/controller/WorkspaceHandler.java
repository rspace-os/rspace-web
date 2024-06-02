package com.researchspace.webapp.controller;

import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.DefaultRecordContext;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RecordManager;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceHandler {
  @Autowired private FolderManager folderManager;

  @Autowired private IPermissionUtils permissionUtils;

  @Autowired private RecordManager recordManager;

  public Notebook createNotebook(long parentRecordId, String notebookName, User user) {
    if (StringUtils.contains(notebookName, "/")) {
      // TODO a nicer way to return an error to the form
      throw new IllegalArgumentException("Name can't contain '/' characters");
    }
    Folder newNotebook =
        folderManager.createNewNotebook(
            parentRecordId, notebookName, new DefaultRecordContext(), user);
    return (Notebook) newNotebook;
  }

  public StructuredDocument createEntry(Notebook notebook, String entryName, User user)
      throws RecordAccessDeniedException {

    if (!permissionUtils.isPermitted(notebook, PermissionType.WRITE, user)) {
      throw new RecordAccessDeniedException(
          "You can not create a record in notebook "
              + notebook.getId()
              + " named "
              + notebook.getName());
    }

    StructuredDocument newEntry = recordManager.createBasicDocument(notebook.getId(), user);
    newEntry.setName(entryName);
    recordManager.save(newEntry, user);

    return newEntry;
  }
}
