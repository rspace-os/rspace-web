package com.researchspace.search.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IndexableFileLocatorTest {
  File dummyFileStore = new File("src/test/resources/TestResources/file_store");
  final int EXPECTED_INDEXABLE_FILES = 6; // 1 in each fs section, + README

  @Test
  void ignoreNonIndexableFolders() {
    File topFolder = dummyFileStore;
    IndexableFileLocator loc = new IndexableFileLocator(dummyFileStore);
    List<File> toIndex = new ArrayList<>();
    loc.doExtractFiles(topFolder, toIndex);
    assertEquals(EXPECTED_INDEXABLE_FILES, toIndex.size());
  }
}
