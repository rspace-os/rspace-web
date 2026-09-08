package com.researchspace.core.util;

import java.io.File;
import java.util.UUID;
import org.apache.commons.io.FilenameUtils;

/**
 * Names files for user uploads without trusting the client-supplied filename, which may contain
 * path traversal ({@code ../../evil}, {@code C:\evil}) and must never be joined to a directory
 * verbatim.
 */
public class SafeTempFiles {

  private SafeTempFiles() {}

  /**
   * Returns a File in {@code dir} with a random name and the original name's extension. The
   * extension must case-insensitively match one of {@code allowedExtensions} (given without dot); a
   * null, empty, or unexpected extension throws {@link UnsupportedFileExtensionException}. The file
   * is not created on disk.
   */
  public static File randomFileIn(File dir, String originalFilename, String... allowedExtensions) {
    String extension = FilenameUtils.getExtension(originalFilename == null ? "" : originalFilename);
    for (String allowed : allowedExtensions) {
      if (allowed.equalsIgnoreCase(extension)) {
        return new File(dir, UUID.randomUUID() + "." + allowed.toLowerCase());
      }
    }
    throw new UnsupportedFileExtensionException(
        "Unsupported file extension for upload: "
            + (extension == null || extension.isEmpty() ? "<none>" : extension));
  }
}
