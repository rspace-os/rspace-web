package com.researchspace.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafeTempFilesTest {

  private static final String UUID_DOT = "[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.";

  @TempDir Path tempDir;

  private File dir() {
    return tempDir.toFile();
  }

  @Test
  void generatesRandomNameKeepingAllowedExtension() {
    File f = SafeTempFiles.randomFileIn(dir(), "myData.zip", "zip", "eln");
    assertEquals(dir(), f.getParentFile());
    assertTrue(f.getName().matches(UUID_DOT + "zip"), f.getName());
  }

  @Test
  void extensionMatchIsCaseInsensitiveAndResultLowercased() {
    File f = SafeTempFiles.randomFileIn(dir(), "MYDATA.ZIP", "zip");
    assertTrue(f.getName().matches(UUID_DOT + "zip"), f.getName());
  }

  @Test
  void posixTraversalNameStaysInsideDirectory() throws IOException {
    File f = SafeTempFiles.randomFileIn(dir(), "../../evil.zip", "zip");
    assertEquals(dir().getCanonicalFile(), f.getCanonicalFile().getParentFile());
    assertTrue(f.getName().matches(UUID_DOT + "zip"), f.getName());
  }

  @Test
  void windowsStyleNameStaysInsideDirectory() throws IOException {
    File f = SafeTempFiles.randomFileIn(dir(), "C:\\stuff\\evil.eln", "zip", "eln");
    assertEquals(dir().getCanonicalFile(), f.getCanonicalFile().getParentFile());
    assertTrue(f.getName().matches(UUID_DOT + "eln"), f.getName());
  }

  @Test
  void veryLongNameProducesShortServerSideName() {
    String longName = StringUtils.repeat("a", 300) + ".enex";
    File f = SafeTempFiles.randomFileIn(dir(), longName, "enex");
    assertTrue(f.getName().length() < 50);
  }

  @Test
  void rejectsNullEmptyMissingAndUnexpectedExtensions() {
    assertThrows(
        UnsupportedFileExtensionException.class,
        () -> SafeTempFiles.randomFileIn(dir(), null, "zip"));
    assertThrows(
        UnsupportedFileExtensionException.class,
        () -> SafeTempFiles.randomFileIn(dir(), "", "zip"));
    assertThrows(
        UnsupportedFileExtensionException.class,
        () -> SafeTempFiles.randomFileIn(dir(), "archive", "zip"));
    assertThrows(
        UnsupportedFileExtensionException.class,
        () -> SafeTempFiles.randomFileIn(dir(), "evil.exe", "zip", "eln"));
  }
}
