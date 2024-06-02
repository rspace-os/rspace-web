package com.researchspace.service.archive.export;

import static com.researchspace.model.record.Folder.EXAMPLES_FOLDER;
import static com.researchspace.model.record.Folder.SHARED_FOLDER_NAME;

import com.researchspace.archive.ArchiveFolder;
import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.ImmutableExportRecordList;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.archive.model.ArchiveModelFactory;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RecordContainerProcessor;
import java.util.ArrayList;
import java.util.List;

/** Extracts lists of records and folders to archive. */
public class ExportArchiveTreeTraversor implements RecordContainerProcessor {

  private RecordIdExtractor delegate;
  private List<ArchiveFolder> folderTree = new ArrayList<ArchiveFolder>();
  private ArchiveModelFactory factory = new ArchiveModelFactory();
  private IArchiveExportConfig exportCfg;

  public ExportArchiveTreeTraversor(IArchiveExportConfig exportCfg) {
    this.delegate = new RecordIdExtractor(false, false, false, null);
    this.exportCfg = exportCfg;
  }

  @Override
  public boolean process(BaseRecord rc) {
    if (isRootFolder(rc) || isFolderIncluded(rc)) {
      delegate.process(rc);
      Folder folder = (Folder) rc;
      if (!folder.isDeleted()) { // RSPAC-998
        ArchiveFolder archiveFolder = factory.createArchiveFolder(folder);
        folderTree.add(archiveFolder);
        return true;
      } else {
        return false; // folder is delted, don't continue
      }

    } else if (isUndeletedNonFolder(rc) || isMediaFile(rc)) {
      delegate.process(rc);
      return true;
    } else {
      // this is a folder of type 'SYSTEM'; we don't want to include these.
      return false;
    }
  }

  private boolean isFolderIncluded(BaseRecord rc) {
    if (!rc.isFolder()) {
      return false;
    }
    if (isSharedFolder(rc)) {
      return false;
    } else if (isExamplesFolder(rc)) {
      return exportCfg.isSelectionScope();
    } else if (isTemplateFolder(rc)) {
      return exportCfg.isSelectionScope()
          || exportCfg.getArchiveType().equals(ArchiveExportConfig.XML);
    }

    return true;
  }

  private boolean isSharedFolder(BaseRecord rc) {
    return rc.getName().equals(SHARED_FOLDER_NAME);
  }

  private boolean isTemplateFolder(BaseRecord rc) {
    return rc.getName().equals(Folder.TEMPLATE_MEDIA_FOLDER_NAME) && ((Folder) rc).isSystemFolder();
  }

  private boolean isExamplesFolder(BaseRecord rc) {
    return rc.getName().equals(EXAMPLES_FOLDER) && ((Folder) rc).isSystemFolder();
  }

  private boolean isRootFolder(BaseRecord rc) {
    return rc.hasType(RecordType.ROOT_MEDIA);
  }

  private boolean isMediaFile(BaseRecord rc) {
    return rc.isMediaRecord();
  }

  private boolean isUndeletedNonFolder(BaseRecord rc) {
    return !rc.isFolder() && !rc.isDeleted();
  }

  public ImmutableExportRecordList getExportRecordList() {
    ExportRecordList rc = new ExportRecordList();
    rc.getFolderTree().addAll(folderTree);
    rc.addAll(delegate.getIds());
    return rc;
  }
}
