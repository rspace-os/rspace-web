package com.researchspace.service.impl;

import static com.researchspace.model.record.Folder.EXAMPLES_FOLDER;
import static com.researchspace.model.record.Folder.SHARED_FOLDER_NAME;

import com.researchspace.archive.model.IExportConfig;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;

public abstract class AbstractExportTreeTraverser {

  public AbstractExportTreeTraverser(IExportConfig exportConfig) {
    this.exportConfig = exportConfig;
  }

  private IExportConfig exportConfig;

  protected boolean isSharedFolder(BaseRecord rc) {
    return rc.getName().equals(SHARED_FOLDER_NAME);
  }

  protected boolean isTemplateFolder(BaseRecord rc) {
    return rc.getName().equals(Folder.TEMPLATE_MEDIA_FOLDER_NAME) && ((Folder) rc).isSystemFolder();
  }

  protected boolean isExamplesFolder(BaseRecord rc) {
    return rc.getName().equals(EXAMPLES_FOLDER) && ((Folder) rc).isSystemFolder();
  }

  protected boolean isRootFolder(BaseRecord rc) {
    return rc.hasType(RecordType.ROOT_MEDIA);
  }

  protected boolean isMediaFile(BaseRecord rc) {
    return rc.isMediaRecord();
  }

  protected boolean isUndeletedNonFolder(BaseRecord rc) {
    return !rc.isFolder() && !rc.isDeleted();
  }

  protected boolean isFolderIncluded(BaseRecord rc) {
    if (!rc.isFolder()) {
      return false;
    }
    if (isSharedFolder(rc)) {
      return false;
    } else if (isExamplesFolder(rc)) {
      return exportConfig.isSelectionScope();
    } else if (isTemplateFolder(rc)) {
      return exportConfig.isSelectionScope();
    }

    return true;
  }
}
