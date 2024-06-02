package com.researchspace.service.archive.export;

import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.ImmutableExportRecordList;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.Folder;
import com.researchspace.service.FolderManager;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ExportIdCollector {

  private FolderManager folderMgr;
  private IArchiveExportConfig exportCfg;

  public ExportRecordList getRecordsToArchive(Long[] exportIds, String[] exportTypes, User user) {
    // go through folders and get all individual records
    ExportRecordList allToExport = new ExportRecordList();

    // these could be several different folder trees
    for (int i = 0; i < exportIds.length; i++) {
      String tp1 = exportTypes[i];
      if (RecordType.isNotebookOrFolder(tp1)) {
        // add all records from notebook or folder
        ImmutableExportRecordList frcds = getRecordIdsFromFolder(exportIds[i], user);
        if (frcds != null) {
          allToExport.add(frcds);
        }
      } else if (RecordType.isDocumentOrTemplate(tp1)) {
        allToExport.add(new GlobalIdentifier(GlobalIdPrefix.SD, exportIds[i]));
      } else if (RecordType.isMediaFile(tp1)) {
        // endure media files will be exported first.
        allToExport.prepend(new GlobalIdentifier(GlobalIdPrefix.GL, exportIds[i]));
      }
    }
    return allToExport;
  }

  // processes a folder tree
  private ImmutableExportRecordList getRecordIdsFromFolder(Long fid, User user) {
    Folder fd1 = folderMgr.getFolder(fid, user);
    if (fd1 == null) {
      return null;
    }
    ExportArchiveTreeTraversor traversor = new ExportArchiveTreeTraversor(exportCfg);
    fd1.process(traversor);
    return traversor.getExportRecordList();
  }
}
