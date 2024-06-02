package com.researchspace.service.archive;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchivalLinkRecord;
import com.researchspace.archive.IArchiveModel;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.model.User;
import com.researchspace.service.RecordContext;
import org.springframework.beans.factory.annotation.Autowired;

/** Imports records and also any users defined in XML archive. */
public class ImportUsersAndRecords extends AbstractImporterStrategyImpl implements ImportStrategy {

  private @Autowired UserImporter userImporter;

  @Override
  void doDatabaseInsertion(
      User importingUser,
      IArchiveModel archiveModel,
      ArchivalImportConfig iconfig,
      ArchivalLinkRecord linkRecord,
      ImportArchiveReport report,
      RecordContext context,
      ProgressMonitor monitor)
      throws Exception {
    userImporter.createUsers(archiveModel, iconfig, importingUser, report);
    insertRecordsToDatabase(
        importingUser, archiveModel, iconfig, linkRecord, report, context, monitor);
  }

  @Override
  String getMonitorMessage() {
    return ("Importing records - ignoring any users defined in the XML archive");
  }
}
