package com.researchspace.zipprocessing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ArchiveIteratorImplTest {

  ArchiveIteratorImpl archiveIt;
  File testZip = null;
  File zipSrc = new File("src/test/resources/archive.zip");
  EntryFileCounter fileprocessor = null;
  EntryCounter entryprocessor = null;

  class EntryFileCounter implements ZipEntryFileProcessor {
    int count = 0;

    @Override
    public void process(File entryAsFile) {
      count++;
    }
  }

  class EntryCounter implements ZipEntryProcessor {
    int count = 0;

    @Override
    public void process(ZipEntry entryAsFile) {
      count++;
    }
  }

  @BeforeEach
  public void setUp() throws Exception {
    archiveIt = new ArchiveIteratorImpl();
    this.testZip = File.createTempFile("testzip", ".zip");
    FileUtils.copyFile(zipSrc, testZip);
    fileprocessor = new EntryFileCounter();
    entryprocessor = new EntryCounter();
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testProcessZip() throws FileNotFoundException, ZipException, IOException {
    archiveIt.processZip(testZip, fileprocessor, f -> true);
    assertEquals(4, fileprocessor.count);
  }

  @Test
  public void testProcessZipEntry() throws FileNotFoundException, ZipException, IOException {
    archiveIt.processZipEntry(testZip, entryprocessor, f -> true);
    assertEquals(4, entryprocessor.count);
  }

  @Test
  public void processZipRejectsEntryOutsideExtractionDirectory() throws IOException {
    File escapingZip = File.createTempFile("escaping", ".zip");
    String escapedName = "escaped-" + System.nanoTime() + ".txt";
    try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(escapingZip))) {
      out.putNextEntry(new ZipEntry("../" + escapedName));
      out.write("outside".getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }

    assertThrows(
        IOException.class, () -> archiveIt.processZip(escapingZip, fileprocessor, f -> true));

    assertEquals(0, fileprocessor.count);
    // the extraction directory is a fresh folder under the temp directory, so "../" lands there
    assertFalse(new File(FileUtils.getTempDirectory(), escapedName).exists());
  }

  /**
   * "./" canonicalises to the extraction directory itself, so a prefix check that requires a
   * trailing separator rejects it. Some zip writers emit that entry, and rejecting it fails the
   * whole archive.
   */
  @Test
  public void processZipAcceptsCurrentDirectoryEntry() throws IOException {
    File zipWithDotEntry = File.createTempFile("dotentry", ".zip");
    try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipWithDotEntry))) {
      out.putNextEntry(new ZipEntry("./"));
      out.closeEntry();
      out.putNextEntry(new ZipEntry("./inside.txt"));
      out.write("inside".getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }

    archiveIt.processZip(zipWithDotEntry, fileprocessor, f -> true);

    assertEquals(1, fileprocessor.count);
  }
}
