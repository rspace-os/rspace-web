package com.researchspace.search.impl;

import com.axiope.search.Indexable;
import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
public abstract class AttachmentSearchBase implements Indexable {

  @Value("${rs.attachment.lucene.index.dir}")
  private final String PATH_TO_SEARCH_INDEX = null;

  /** */
  public static final String DEFAULTINDEX_DIRECTORY = "LuceneFTsearchIndices";

  private String indexFolder = DEFAULTINDEX_DIRECTORY;
  private File index = null;

  /*
   * Sets indexing folder to location set in property file, else uses default.
   */
  void setIndexFolder() throws IOException {
    if (index == null) {
      if (PATH_TO_SEARCH_INDEX != null) {
        indexFolder = PATH_TO_SEARCH_INDEX;
      } else {
        indexFolder = DEFAULTINDEX_DIRECTORY;
      }
      index = new File(indexFolder);
    }
    log.info("Setting index folder to {}", index.getAbsolutePath());

    if (!index.exists()) {
      log.info("Creating index folder ...");
      boolean mkdir = index.mkdir();
      if (!mkdir) {
        throw new IOException("Lucene index could not be created for path " + getIndexFolderPath());
      }
      if (!index.canWrite() || !index.isDirectory()) {
        throw new IOException(
            "Lucene index must be a writable directory for path " + getIndexFolderPath());
      }
    }
  }

  /**
   * Sets index folder directly, for use in testing
   *
   * @param indexFolder
   */
  public void setIndexFolderDirectly(File indexFolder) {
    if (!indexFolder.isDirectory() || !indexFolder.canWrite()) {
      throw new IllegalArgumentException("Needs writeable folder for search index");
    }
    log.info("Setting index folder directly to {}", indexFolder.getAbsolutePath());
    this.index = indexFolder;
  }

  public String getIndexFolderPath() {
    return indexFolder;
  }

  public File getIndexFolder() throws IOException {
    if (index == null) {
      log.info("Index folder is null, setting up ....");
      setIndexFolder();
    }
    return index;
  }
}
