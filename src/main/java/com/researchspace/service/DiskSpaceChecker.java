package com.researchspace.service;

import java.io.File;

public interface DiskSpaceChecker {

  /**
   * Checks is amount of available disk space mets the RSpace requirements for starting archive
   * process.
   *
   * @return true if archive process can be started
   */
  boolean canStartArchiveProcess();

  int getCurrentlyAllowedArchiveSizeMB();

  /**
   * Checks if amount of available disk space mets the RSpace requirements for copying the file into
   * archive folder and continuing export process. Throws DiskSpaceLimitException otherwise.
   *
   * @throws DiskSpaceLimitException if there is not enough disk space to copy the file during
   *     archive process
   */
  void assertEnoughDiskSpaceToCopyFileIntoArchiveDir(File resourceToCopy, File archiveDir)
      throws DiskSpaceLimitException;

  void assertEnoughDiskSpaceToCopyFileSizeIntoArchiveDir(long fileSizeInBytes, File archiveDir)
      throws DiskSpaceLimitException;

  /*
   * =========================================================================================
   *   deployment property setters/getters, setters are public so can be changed in IT tests
   * =========================================================================================
   */
  int getMaxArchiveSizeMB();

  void setMaxArchiveSizeMB(String maxArchiveSizeMB);

  int getMinSpaceRequiredToStartArchiveExportMB();

  void setSpaceRequiredToStartArchiveExportMB(String minSpaceRequiredToStartArchiveMB);
}
