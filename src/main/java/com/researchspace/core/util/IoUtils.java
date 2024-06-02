package com.researchspace.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

public class IoUtils {
  /**
   * Create a secure directory for temporary files if it doesn't already exist, otherwise return the
   * file path
   *
   * @return the filepath of the secure directory
   */
  public static Path createOrGetSecureTempDirectory() throws IOException {
    Path osTempDir = Path.of(System.getProperty("java.io.tmpdir"));
    String rspaceTempFolder = "rspaceTmp";
    Optional<Path> existingTmpDir;

    try (Stream<Path> files = Files.list(osTempDir)) {
      existingTmpDir = files.filter(file -> file.startsWith(rspaceTempFolder)).findFirst();
    }

    if (existingTmpDir.isPresent()) {
      return existingTmpDir.get();
    }

    Path tmpdir = Files.createTempDirectory(osTempDir, rspaceTempFolder);
    File tmpDirAsFile = tmpdir.toFile();
    tmpDirAsFile.setReadable(true, true);
    tmpDirAsFile.setWritable(true, true);
    tmpDirAsFile.setExecutable(true, true);
    tmpDirAsFile.deleteOnExit();
    return tmpdir;
  }
}
