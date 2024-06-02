package com.researchspace.service.archive;

import com.researchspace.archive.ArchivalFileNotExistException;
import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.core.util.progress.ProgressMonitor;
import java.io.File;

/**
 * Top level interface for import of RSpace archives back into RSpace.
 *
 * <h2>Import strategy </h2>
 *
 * A validation phase precedes the import of records into the DB. This validation phase also checks
 * that the schema version attributes of documents are compatible with the database version.
 *
 * <h2>Altering the schema or DB version</h2>
 *
 * Need to work out if changed schema will impact on import back to the DB.
 */
public interface ArchiveImporterManager {
  /**
   * Imports an archive. If validation of the archive contents fails, then import is not attempted.
   *
   * @param zipFile the zip file
   * @param iconfig the import configuration
   * @param monitor
   * @return A report on the success or otherwise of the import operation.
   * @throws ArchivalFileNotExistException if archive file not found
   * @throws ImportFailureException if processing of archive fails
   */
  public ImportArchiveReport importArchive(
      File zipFile,
      ArchivalImportConfig iconfig,
      ProgressMonitor monitor,
      ImportStrategy importStrategy);
}
