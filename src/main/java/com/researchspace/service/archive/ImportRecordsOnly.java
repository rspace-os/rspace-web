package com.researchspace.service.archive;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchivalLinkRecord;
import com.researchspace.archive.IArchiveModel;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.model.User;
import com.researchspace.service.RecordContext;
import java.io.IOException;
import java.net.URISyntaxException;

/** Imports records/notebooks/files only. Ignores and users and messages sections in the archive */
public class ImportRecordsOnly extends AbstractImporterStrategyImpl implements ImportStrategy {

  @Override
  void doDatabaseInsertion(
      User importingUser,
      IArchiveModel archiveModel,
      ArchivalImportConfig iconfig,
      ArchivalLinkRecord linkRecord,
      ImportArchiveReport report,
      RecordContext context,
      ProgressMonitor monitor)
      throws IOException, URISyntaxException {
    insertRecordsToDatabase(
        importingUser, archiveModel, iconfig, linkRecord, report, context, monitor);
  }

  @Override
  String getMonitorMessage() {
    return "Importing records";
  }
}
