package com.researchspace.service.archive;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.IArchiveModel;
import java.io.File;

public interface IArchiveParser {

  public IArchiveModel parse(File folder, ImportArchiveReport report);

  /**
   * Validates the contents of an archive
   *
   * @param zipFile The zip archive file
   * @param report the report to populate
   * @param iconfig the configuration of the import.
   * @return
   */
  public IArchiveModel loadArchive(
      File zipFile, ImportArchiveReport report, ArchivalImportConfig iconfig);
}
