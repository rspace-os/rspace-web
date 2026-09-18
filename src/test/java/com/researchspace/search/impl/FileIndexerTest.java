package com.researchspace.search.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.axiope.search.FileSearchResult;
import com.researchspace.model.User;
import com.researchspace.testutils.TestFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FileIndexerTest {

  @TempDir File indexFolder;

  @TempDir File dataFolder;

  private final FileIndexer indexer = new FileIndexer();

  @AfterEach
  void tearDown() throws IOException {
    indexer.close();
  }

  @Test
  void deleteReindexRecoversFromIndexWrittenByUnsupportedLuceneVersion() throws IOException {
    File staleIndexFile = writePreLucene8SegmentsFile();
    indexer.setIndexFolderDirectly(indexFolder);

    indexer.init(true);

    assertTrue(indexer.isInitialised());
    assertEquals(0, indexer.getWriter().getDocStats().numDocs);
    assertFalse(staleIndexFile.exists());
  }

  @Test
  void appendModeRecoversFromIndexWrittenByUnsupportedLuceneVersion() throws IOException {
    File staleIndexFile = writePreLucene8SegmentsFile();
    indexer.setIndexFolderDirectly(indexFolder);

    indexer.init(false);

    assertTrue(indexer.isInitialised());
    assertEquals(0, indexer.getWriter().getDocStats().numDocs);
    assertFalse(staleIndexFile.exists());
  }

  @Test
  void appendModePreservesExistingCompatibleIndex() throws IOException {
    File toIndex = new File(dataFolder, "indexme.txt");
    FileUtils.writeStringToFile(toIndex, "some searchable content", UTF_8);
    indexer.setIndexFolderDirectly(indexFolder);
    indexer.init(false);
    indexer.indexFile(toIndex);
    indexer.close();

    FileIndexer reopened = new FileIndexer();
    reopened.setIndexFolderDirectly(indexFolder);
    reopened.init(false);
    assertEquals(1, reopened.getWriter().getDocStats().numDocs);
    reopened.close();
  }

  @Test
  void concurrentlyIndexedFilesAreAllSearchable() throws Exception {
    List<String> searchTerms =
        List.of("alphaunique", "bravounique", "charlieunique", "deltaunique");
    List<File> files = new ArrayList<>();
    for (int i = 0; i < searchTerms.size(); i++) {
      File file = new File(dataFolder, i + ".txt");
      FileUtils.writeStringToFile(file, "content containing " + searchTerms.get(i), UTF_8);
      files.add(file);
    }

    indexer.setIndexFolderDirectly(indexFolder);
    indexer.init(true);
    ExecutorService executor = Executors.newFixedThreadPool(files.size());
    try {
      List<Future<?>> indexingTasks = new ArrayList<>();
      for (File file : files) {
        indexingTasks.add(
            executor.submit(
                () -> {
                  indexer.indexFile(file);
                  return null;
                }));
      }
      for (Future<?> task : indexingTasks) {
        task.get(10, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    LuceneSearchStrategy searcher = new LuceneSearchStrategy();
    searcher.setIndexFolderDirectly(indexFolder);
    User user = TestFactory.createAnyUser("any");
    for (int i = 0; i < files.size(); i++) {
      List<FileSearchResult> results = searcher.searchFiles(searchTerms.get(i), user);
      String expectedFileName = files.get(i).getName();
      assertTrue(
          results.stream().map(FileSearchResult::getFileName).anyMatch(expectedFileName::equals));
    }
  }

  /**
   * Writes a segments file whose header declares format version 6, the version written by the
   * Lucene 5.5.5 in use before the Hibernate Search 6 upgrade, which current Lucene refuses to open
   * (it reads indexes created with release 8.0 and later only). Also writes a segment data file
   * alongside it, and returns that file so tests can assert the stale index was wiped: an empty
   * fresh index never writes one, whereas its first commit can reuse the name segments_1.
   */
  private File writePreLucene8SegmentsFile() throws IOException {
    try (Directory dir = FSDirectory.open(indexFolder.toPath());
        IndexOutput out = dir.createOutput("segments_1", IOContext.DEFAULT)) {
      CodecUtil.writeHeader(out, "segments", 6);
    }
    File staleSegmentData = new File(indexFolder, "_0.cfs");
    FileUtils.writeStringToFile(staleSegmentData, "stale segment data", UTF_8);
    return staleSegmentData;
  }
}
