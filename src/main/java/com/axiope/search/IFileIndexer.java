package com.axiope.search;

import java.io.File;
import java.io.IOException;
import org.springframework.scheduling.annotation.Async;

public interface IFileIndexer {

  /**
   * Indexes and commits a file using Lucene, configurable to run backend. Clients should check that
   * accept(file) returns <code>true</code> before calling this method to avoid overwhelming the
   * task executor with tasks that may not run (e.g. image files)
   *
   * @param file File the file to index
   * @throws IOException
   */
  @Async(value = "indexTaskExecutor")
  void indexFile(File file) throws IOException;

  /**
   * Boolean test as to whether this indexer has been initialised.
   *
   * @return
   */
  boolean isInitialised();

  /**
   * Closes the indexer.
   *
   * @throws IOException
   */
  void close() throws IOException;

  /**
   * Initialises the index, if not already initialised. Equivalent to <code>init(false)</code>
   *
   * @throws IOException
   */
  void init() throws IOException;

  /**
   * Initialises the index, if not already initialised.
   *
   * @param deleteIndex {@link Boolean} as to whether any existing index should be deleted or not.
   * @throws IOException
   */
  void init(boolean deleteIndex) throws IOException;

  /**
   * Indexes the file store
   *
   * @param failFast Whether indexing should continue even in a file throws an unexpected exception
   *     when indexing.
   * @return
   * @throws IOException
   */
  int indexFileStore(boolean failFast) throws IOException;

  /**
   * Deletes everything in the index
   *
   * @throws IOException
   */
  void deleteAll() throws IOException;

  void setIndexFolderDirectly(File indexFolder);

  /**
   * Boolean test for whether this file can be indexed.
   *
   * @param file
   * @return boolean true if file can be indexed; false otherwise
   */
  boolean accept(File file);
}
