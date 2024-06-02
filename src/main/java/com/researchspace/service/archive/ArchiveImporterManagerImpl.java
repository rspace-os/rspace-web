package com.researchspace.service.archive;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.IArchiveModel;
import com.researchspace.core.util.progress.ProgressMonitor;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ArchiveImporterManagerImpl implements ArchiveImporterManager {

  private static final Logger log = LoggerFactory.getLogger(ArchiveImporterManagerImpl.class);

  private @Autowired IArchiveParser archiveParser;
  private @Autowired ArchiveModelToDatabaseSaver databaseSaver;

  @Override
  public ImportArchiveReport importArchive(
      File zipFile,
      ArchivalImportConfig iconfig,
      ProgressMonitor monitor,
      ImportStrategy importStrategy) {
    ImportArchiveReport report = new ImportArchiveReport();
    try {
      IArchiveModel archiveModel = archiveParser.loadArchive(zipFile, report, iconfig);
      if (!report.isValidationSuccessful()) {
        report.getErrorList().addErrorMsg("Validation of archive was not successful");
        log.error(
            "Validation of archive was not successful: {}",
            report.getErrorList().getAllErrorMessagesAsStringsSeparatedBy("\n"));
        return report;
      }
      databaseSaver.saveArchiveToDB(iconfig, report, archiveModel, monitor, importStrategy);
    } catch (ImportFailureException e) {
      String msg = " Error during import: " + e.getMessage();
      if (e.getCause() != null) {
        msg = msg + " caused by " + e.getCause();
      }
      log.error(msg);
      report.setValidationComplete(true);
      report.setValidationResult(ImportValidationRule.GENERAL_ARCHIVE_STRUCTURE, false);
    } catch (Exception general) {
      log.error(general.getMessage());
      report.setValidationComplete(true);
      report.setValidationResult(ImportValidationRule.UNKNOWN, false);
    }

    return report;
  }

  /*
   * ==============
   *  for tests
   * ==============
   */
  public void setDatabaseSaver(ArchiveModelToDatabaseSaver databaseSaver) {
    this.databaseSaver = databaseSaver;
  }
}
