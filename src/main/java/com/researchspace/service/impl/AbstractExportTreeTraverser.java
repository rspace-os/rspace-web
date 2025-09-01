package com.researchspace.service.impl;

import static com.researchspace.model.record.Folder.EXAMPLES_FOLDER;

import com.researchspace.archive.model.IExportConfig;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;

public abstract class AbstractExportTreeTraverser {

  public AbstractExportTreeTraverser(IExportConfig exportConfig) {
    this.exportConfig = exportConfig;
  }

  private IExportConfig exportConfig;

  protected boolean isSharedFolder(Folder fld) {
    return fld.isTopLevelSharedFolder();
  }

  protected boolean isTemplateFolder(Folder fld) {
    return fld.getName().equals(Folder.TEMPLATE_MEDIA_FOLDER_NAME) && fld.isSystemFolder();
  }

  protected boolean isExamplesFolder(Folder fld) {
    return fld.getName().equals(EXAMPLES_FOLDER) && fld.isSystemFolder();
  }

  protected boolean isRootFolder(BaseRecord rc) {
    return rc.hasType(RecordType.ROOT_MEDIA);
  }

  protected boolean isUndeletedNonFolder(BaseRecord rc) {
    return !rc.isFolder() && !rc.isDeleted();
  }

  protected boolean isFolderIncluded(BaseRecord rc) {
    if (!rc.isFolder()) {
      return false;
    }
    if (isSharedFolder((Folder) rc)) {
      return false;
    } else if (isExamplesFolder((Folder) rc)) {
      return exportConfig.isSelectionScope();
    } else if (isTemplateFolder((Folder) rc)) {
      return exportConfig.isSelectionScope();
    }

    return true;
  }
}
