package com.researchspace.service.archive;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.IArchiveModel;
import com.researchspace.core.util.progress.ProgressMonitor;

/** Defines methods for saving a parsed archive to the database. */
public interface ArchiveModelToDatabaseSaver {

  /**
   * Takes a parsed archive and saves to the database.
   *
   * @param iconfig
   * @param report
   * @param archiveModel
   * @param monitor
   */
  public void saveArchiveToDB(
      ArchivalImportConfig iconfig,
      ImportArchiveReport report,
      IArchiveModel archiveModel,
      ProgressMonitor monitor,
      ImportStrategy strategy);
}
