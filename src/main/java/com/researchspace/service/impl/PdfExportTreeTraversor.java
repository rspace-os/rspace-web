package com.researchspace.service.impl;

import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.ImmutableExportRecordList;
import com.researchspace.export.pdf.ExportToFileConfig;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RecordContainerProcessor;
import com.researchspace.service.archive.export.RecordIdExtractor;

/** Extracts lists of records and folders to archive. */
public class PdfExportTreeTraversor extends AbstractExportTreeTraverser
    implements RecordContainerProcessor {

  private RecordIdExtractor delegate;

  public PdfExportTreeTraversor(ExportToFileConfig exportCfg, RecordIdExtractor idextractor) {
    super(exportCfg);
    this.delegate = idextractor;
  }

  @Override
  public boolean process(BaseRecord rc) {
    if (isRootFolder(rc) || isFolderIncluded(rc)) {
      delegate.process(rc);
      Folder folder = (Folder) rc;
      if (!folder.isDeleted()) { // RSPAC-998
        return true;
      } else {
        return false; // folder is delted, don't continue
      }

    } else if (isUndeletedNonFolder(rc)) {
      delegate.process(rc);
      return true;
    } else {
      // this is a folder of type 'SYSTEM'; we don't want to include these.
      return false;
    }
  }

  public ImmutableExportRecordList getExportRecordList() {
    ExportRecordList rc = new ExportRecordList();
    rc.addAll(delegate.getIds());
    return rc;
  }
}
