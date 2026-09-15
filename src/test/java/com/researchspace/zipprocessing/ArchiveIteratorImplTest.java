package com.researchspace.zipprocessing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
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
}
