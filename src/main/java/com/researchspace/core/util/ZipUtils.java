package com.researchspace.core.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.LongSupplier;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utility class for high-level manipulation of Zip files */
public class ZipUtils {
  private static final Logger log = LoggerFactory.getLogger(ZipUtils.class);

  /**
   * @see ZipUtils#createZip(String, File...)
   */
  public static void createZip(File zipToCreate, File... contentRoot) throws IOException {
    createZip(zipToCreate.getAbsolutePath(), contentRoot);
  }

  /**
   * Creates a zip file at the specified path with the contents of the specified directory. NB:
   *
   * @param zipPath The full path of the archive to create. e.g., c:/temp/archive.zip
   * @param directoryPath The path of the directory where the archive will be created. I.e., the
   *     folder /files that contains the content you want to zip.
   * @throws IOException If anything goes wrong
   */
  public static void createZip(String zipPath, File... directoryPath) throws IOException {
    FileOutputStream fOut = null;
    BufferedOutputStream bOut = null;
    ZipArchiveOutputStream tOut = null;

    try {
      fOut = new FileOutputStream(zipPath);
      bOut = new BufferedOutputStream(fOut);
      tOut = new ZipArchiveOutputStream(bOut);
      for (File toAdd : directoryPath) {
        addFileToZip(tOut, toAdd.getAbsolutePath(), "");
      }

    } finally {
      if (tOut != null) {
        tOut.finish();
        tOut.close();
      }
      if (bOut != null) {
        bOut.close();
      }
      if (fOut != null) {
        fOut.close();
      }
    }
  }

  /**
   * Creates a zip entry for the path specified with a name built from the base passed in and the
   * file/directory name. If the path is a directory, a recursive call is made such that the full
   * directory is added to the zip.
   *
   * @param zOut The zip file's output stream
   * @param path The filesystem path of the file/directory being added
   * @param base The base prefix to for the name of the zip file entry
   * @throws IOException If anything goes wrong
   */
  private static void addFileToZip(ZipArchiveOutputStream zOut, String path, String base)
      throws IOException {
    File f = new File(path);
    String entryName = base + f.getName();
    ZipArchiveEntry zipEntry = new ZipArchiveEntry(f, entryName);

    zOut.putArchiveEntry(zipEntry);

    if (f.isFile()) {
      try (FileInputStream fInputStream = new FileInputStream(f)) {
        IOUtils.copy(fInputStream, zOut);
        zOut.closeArchiveEntry();
      }

    } else {
      zOut.closeArchiveEntry();
      File[] children = f.listFiles();
      if (children != null) {
        for (File child : children) {
          addFileToZip(zOut, child.getAbsolutePath(), entryName + "/");
        }
      }
    }
  }

  /**
   * Extract zip file at the specified destination path. NB:archive must consist of a single root
   * folder containing everything else.
   *
   * <p>Extraction is safe against archives that originate from user uploads: an absolute entry name
   * is rejected, and each entry's canonical destination must resolve inside the extraction
   * directory (covering {@code ..} traversal and backslash variants). Extraction also stops once
   * the archive exceeds {@link ZipExtractionLimits#DEFAULTS} (bytes written, entry count,
   * compression ratio, free disk space), so a zip bomb cannot fill the partition. A rejection or
   * I/O failure aborts and removes everything the extraction created; it does not restore
   * pre-existing files that a failed run has already overwritten, so extract into a fresh directory
   * when the destination may hold content worth keeping.
   *
   * @param archivePath path to zip file, e.g., a/b/c/d.archive.zip
   * @param destinationPath The folder into which the zip archive will be extracted.
   */
  public static String extractZip(String archivePath, String destinationPath) throws IOException {
    return extractZip(new File(archivePath), new File(destinationPath));
  }

  /**
   * Overloaded method taking files as arguments.
   *
   * @param archiveFile
   * @param destinationFolder the folder into which zip contents should be put.
   * @return The name of top-level folder or <code>null</code> if no top-level folder in zip
   */
  public static String extractZip(File archiveFile, File destinationFolder) throws IOException {
    return extractZip(archiveFile, destinationFolder, ZipExtractionLimits.DEFAULTS);
  }

  /**
   * Extracts with caller-supplied limits instead of {@link ZipExtractionLimits#DEFAULTS}.
   *
   * @throws ZipExtractionLimitException if a limit is exceeded; anything written is removed first
   */
  public static String extractZip(
      File archiveFile, File destinationFolder, ZipExtractionLimits limits) throws IOException {
    return extractZip(archiveFile, destinationFolder, limits, destinationFolder::getUsableSpace);
  }

  /** Lets tests simulate the partition filling up part-way through an extraction. */
  static String extractZip(
      File archiveFile,
      File destinationFolder,
      ZipExtractionLimits limits,
      LongSupplier usableSpace)
      throws IOException {
    if (!destinationFolder.exists()) {
      boolean dirMade = destinationFolder.mkdir();
      if (!dirMade) {
        throw new IllegalStateException("Couldn't make folder to unzip into");
      }
    }
    return unzipFolder(archiveFile, destinationFolder, limits, usableSpace);
  }

  /**
   * Unzips a zip file into the given destination directory.
   *
   * <p>The archive file MUST have a unique "root" folder. This root folder is skipped when
   * unarchiving.
   *
   * @return The name of top-level folder or <code>null</code> if no top-level folder
   */
  private static String unzipFolder(
      File archiveFile,
      File zipDestinationFolder,
      ZipExtractionLimits limits,
      LongSupplier usableSpace)
      throws IOException {

    final int bufferSize = 65536;
    File topLevelDir = null;
    String rootPath = zipDestinationFolder.getCanonicalPath();
    String rootPrefix = rootPath.endsWith(File.separator) ? rootPath : rootPath + File.separator;
    // paths this extraction created, so a rejection can remove exactly what it wrote
    Set<File> created = new LinkedHashSet<>();
    ExtractionBudget budget = new ExtractionBudget(limits, archiveFile, usableSpace);

    try (ZipFile zipFile = new ZipFile(archiveFile)) {
      byte[] buf = new byte[bufferSize];
      budget.checkFreeDiskSpace();

      Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
      while (entries.hasMoreElements()) {
        ZipArchiveEntry zipEntry = entries.nextElement();
        budget.countEntry(zipEntry);
        String name = zipEntry.getName();
        name = name.replace('\\', '/');
        if (name.startsWith("/") || name.matches("^[A-Za-z]:.*")) {
          throw new InvalidArchiveException("absolute entry name: " + zipEntry.getName());
        }

        File destinationFile = new File(zipDestinationFolder, name);
        String canonical = destinationFile.getCanonicalPath();
        // the extraction root itself (e.g. a leading "./" entry) is inside the directory
        if (!canonical.equals(rootPath) && !canonical.startsWith(rootPrefix)) {
          throw new InvalidArchiveException(
              "entry resolves outside the extraction directory: " + zipEntry.getName());
        }
        if (name.endsWith("/")) {
          if (!destinationFile.isDirectory()) {
            File topmostMissingDir = topmostMissingAncestor(destinationFile, rootPrefix);
            if (!destinationFile.mkdirs()) {
              // typically a file entry earlier in the archive already claimed this path
              throw new InvalidArchiveException(
                  "could not create directory for entry: " + zipEntry.getName());
            }
            if (topmostMissingDir != null) {
              created.add(topmostMissingDir);
            }
          }
          if (topLevelDir == null) {
            topLevelDir = destinationFile;
          }
          continue;
        }

        // track for rollback only what this extraction creates, never pre-existing content
        if (!destinationFile.exists()) {
          created.add(destinationFile);
        }
        budget.startEntry(zipEntry);
        try (InputStream entryStream = zipFile.getInputStream(zipEntry);
            FileOutputStream fos = new FileOutputStream(destinationFile)) {
          int n;
          while ((n = entryStream.read(buf)) != -1) {
            if (n > 0) {
              fos.write(buf, 0, n);
              budget.countWritten(n);
            }
          }
        }
      }
      budget.checkFreeDiskSpace();

    } catch (InvalidArchiveException e) {
      log.warn("Rejected archive {}: {}", archiveFile.getName(), e.getMessage());
      deleteAll(created);
      throw e;
    } catch (IOException e) {
      log.warn("Unzip failed: {}", e.getMessage(), e);
      deleteAll(created);
      throw e;
    }

    return (topLevelDir != null) ? topLevelDir.getAbsolutePath() : null;
  }

  /**
   * Tracks what an extraction has written against its {@link ZipExtractionLimits}. The ratio
   * compares bytes written with the archive file's length.
   */
  private static final class ExtractionBudget {
    private static final long DISK_CHECK_INTERVAL_BYTES = 16L * 1024 * 1024;
    // directories and tiny files take a disk block each but add little or nothing to the bytes
    private static final int DISK_CHECK_INTERVAL_ENTRIES = 1024;

    private final ZipExtractionLimits limits;
    private final long archiveCompressedBytes;
    private final LongSupplier usableSpace;
    private int entries;
    private long totalWritten;
    private long writtenSinceDiskCheck;
    private String entryName = "";

    ExtractionBudget(ZipExtractionLimits limits, File archiveFile, LongSupplier usableSpace) {
      this.limits = limits;
      this.archiveCompressedBytes = Math.max(1, archiveFile.length());
      this.usableSpace = usableSpace;
    }

    void countEntry(ZipArchiveEntry entry) throws ZipExtractionLimitException {
      entries++;
      if (limits.maxEntries() > 0 && entries > limits.maxEntries()) {
        throw new ZipExtractionLimitException(
            ZipExtractionLimitException.Limit.ENTRY_COUNT,
            limits.maxEntries(),
            "more than " + limits.maxEntries() + " entries, at " + entry.getName());
      }
      if (entries % DISK_CHECK_INTERVAL_ENTRIES == 0) {
        checkFreeDiskSpace();
      }
    }

    void startEntry(ZipArchiveEntry entry) {
      entryName = entry.getName();
    }

    void countWritten(int bytes) throws ZipExtractionLimitException {
      totalWritten += bytes;
      writtenSinceDiskCheck += bytes;
      if (limits.maxExpandedBytes() > 0 && totalWritten > limits.maxExpandedBytes()) {
        throw new ZipExtractionLimitException(
            ZipExtractionLimitException.Limit.EXPANDED_SIZE,
            limits.maxExpandedBytes(),
            "expands past " + limits.maxExpandedBytes() + " bytes at entry " + entryName);
      }
      long ratio = limits.maxCompressionRatio();
      if (ratio > 0
          && totalWritten > limits.ratioFloorBytes()
          && totalWritten > ratio * archiveCompressedBytes) {
        throw new ZipExtractionLimitException(
            ZipExtractionLimitException.Limit.COMPRESSION_RATIO,
            ratio,
            "archive inflates more than " + ratio + " times, at entry " + entryName);
      }
      if (writtenSinceDiskCheck >= DISK_CHECK_INTERVAL_BYTES) {
        writtenSinceDiskCheck = 0;
        checkFreeDiskSpace();
      }
    }

    void checkFreeDiskSpace() throws ZipExtractionLimitException {
      if (limits.minFreeDiskBytes() > 0 && usableSpace.getAsLong() < limits.minFreeDiskBytes()) {
        throw new ZipExtractionLimitException(
            ZipExtractionLimitException.Limit.FREE_DISK_SPACE,
            limits.minFreeDiskBytes(),
            "under " + limits.minFreeDiskBytes() + " bytes free on the destination partition");
      }
    }
  }

  private static void deleteAll(Set<File> created) {
    for (File file : created) {
      FileUtils.deleteQuietly(file);
    }
  }

  /**
   * Returns the highest ancestor of {@code dir} (itself included) that does not yet exist, staying
   * inside the extraction root; deleting it recursively after a {@code mkdirs} removes every
   * directory that call created and nothing that existed before.
   */
  private static File topmostMissingAncestor(File dir, String rootPrefix) throws IOException {
    File topmostMissing = null;
    for (File d = dir;
        d != null && d.getCanonicalPath().startsWith(rootPrefix);
        d = d.getParentFile()) {
      if (!d.exists()) {
        topmostMissing = d;
      }
    }
    return topmostMissing;
  }
}
