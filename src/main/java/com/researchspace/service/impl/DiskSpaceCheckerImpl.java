package com.researchspace.service.impl;

import com.researchspace.core.util.IoUtils;
import com.researchspace.service.DiskSpaceChecker;
import com.researchspace.service.DiskSpaceLimitException;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DiskSpaceCheckerImpl implements DiskSpaceChecker {

  private Logger log = LoggerFactory.getLogger(DiskSpaceCheckerImpl.class);

  /* handle to a file in temp folder */
  private File tempFile;

  private int minSpaceRequiredToStartArchiveExportMB;

  private int maxArchiveSizeMB;

  @Override
  public boolean canStartArchiveProcess() {
    return getAvailableSpaceMBsOnTmpFolderPartition() > 0;
  }

  @Override
  public int getCurrentlyAllowedArchiveSizeMB() {
    int availableSpace = getAvailableSpaceMBsOnTmpFolderPartition();
    if (maxArchiveSizeMB == 0) {
      return availableSpace;
    } else {
      return availableSpace > maxArchiveSizeMB ? maxArchiveSizeMB : availableSpace;
    }
  }

  @Override
  public void assertEnoughDiskSpaceToCopyFileIntoArchiveDir(File resourceToCopy, File archiveDir)
      throws DiskSpaceLimitException {

    long resourceSize = resourceToCopy.exists() ? FileUtils.sizeOf(resourceToCopy) : 0;
    assertEnoughDiskSpaceToCopyFileSizeIntoArchiveDir(resourceSize, archiveDir);
  }

  @Override
  public void assertEnoughDiskSpaceToCopyFileSizeIntoArchiveDir(long byteLength, File archiveDir)
      throws DiskSpaceLimitException {

    long currentArchiveSize =
        (archiveDir != null && archiveDir.exists()) ? FileUtils.sizeOf(archiveDir) : 0;
    int totalMBs = bytesToMBs(currentArchiveSize + byteLength);

    if (totalMBs > maxArchiveSizeMB && maxArchiveSizeMB > 0) {
      throw new DiskSpaceLimitException(
          "Constructed archive is larger than " + maxArchiveSizeMB + " MB limit");
    }

    int availableMBs = getAvailableSpaceMBsOnTmpFolderPartition();
    if (totalMBs > availableMBs) {
      throw new DiskSpaceLimitException(
          "Constructed archive is getting larger ("
              + totalMBs
              + " MB) than required available disk space");
    }
  }

  /**
   * @return amount of available space (in megabytes) on the partition holding tmp files
   * @throws IOException
   */
  private int getAvailableSpaceMBsOnTmpFolderPartition() {
    long usableSpace = 0;
    try {
      File file = getTempFile();
      usableSpace = file.getUsableSpace();
    } catch (IOException e) {
      log.warn("can't read usable space: {}", e.getMessage());
    }
    int usableSpaceMB = bytesToMBs(usableSpace);
    return usableSpaceMB - minSpaceRequiredToStartArchiveExportMB;
  }

  private int bytesToMBs(long bytes) {
    return (int) (bytes / FileUtils.ONE_MB);
  }

  private File getTempFile() throws IOException {
    if (tempFile == null) {
      File secureTmpDir = IoUtils.createOrGetSecureTempDirectory().toFile();
      tempFile = File.createTempFile("rspace", "diskSpaceChecker", secureTmpDir);
    }
    return tempFile;
  }

  /*
   * =====================================================
   *  deployment property setters/getters, for testing
   * =====================================================
   */

  @Override
  @Value("${archive.minSpaceRequiredToStartMB}")
  public void setSpaceRequiredToStartArchiveExportMB(String minSpaceRequiredToStartArchiveMB) {
    try {
      Float parsedValue = Float.valueOf(minSpaceRequiredToStartArchiveMB);
      this.minSpaceRequiredToStartArchiveExportMB = parsedValue > 0 ? parsedValue.intValue() : 0;
    } catch (NumberFormatException | NullPointerException e) {
      log.warn(
          "Couldn't set minSpaceRequiredToStartMB [{}], no limit will be used",
          minSpaceRequiredToStartArchiveMB);
    }
  }

  @Override
  public int getMinSpaceRequiredToStartArchiveExportMB() {
    return minSpaceRequiredToStartArchiveExportMB;
  }

  @Override
  @Value("${archive.maxExpandedSizeMB}")
  public void setMaxArchiveSizeMB(String maxArchiveSizeMB) {
    try {
      Float parsedValue = Float.valueOf(maxArchiveSizeMB);
      this.maxArchiveSizeMB = parsedValue > 0 ? parsedValue.intValue() : 0;
    } catch (NumberFormatException | NullPointerException e) {
      log.warn("Couldn't set maxExpandedSizeMB [{}], no limit will be used", maxArchiveSizeMB);
    }
  }

  @Override
  public int getMaxArchiveSizeMB() {
    return maxArchiveSizeMB;
  }

  /*
   * ==============
   *   for tests
   * ==============
   */
  public void setTempFile(File tempFile) {
    this.tempFile = tempFile;
  }
}
