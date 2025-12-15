package com.researchspace.service.impl;

import com.axiope.search.IFileIndexer;
import com.researchspace.dao.TextSearchDao;
import com.researchspace.service.IApplicationInitialisor;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/** Performs optional search indexing based at startup based on a property value */
@Component("LuceneSearchIndexInitialisor")
public class LuceneSearchIndexInitialisor implements IApplicationInitialisor {

  private Logger log = LoggerFactory.getLogger(AbstractAppInitializor.class);

  // if not not null, should be a boolean value on whether to re-index text index on startup or not
  @Value("${rs.indexOnstartup}")
  private String indexOnstartup;

  private @Autowired TextSearchDao textDao;
  private @Autowired IFileIndexer indexer;

  @Override
  public void onInitialAppDeployment() {
    try {
      log.info("Clean initialisation of Lucene index");
      indexer.init(true);
    } catch (IOException e) {
      log.error("failure to initialise  Lucene FileStore index. Exiting index {}", e.getMessage());
    }
  }

  @Override
  public void onAppVersionUpdate() {}

  @Override
  public void onAppStartup(ApplicationContext applicationContext) {
    if (!"true".equals(indexOnstartup)) {
      log.info("'rs.indexOnstartup' not true, was [{}]. *Not* reindexing", indexOnstartup);
      return;
    }

    log.info("Database text indexing starting...");
    try {
      textDao.indexText();
      log.info("Database text indexing complete");
    } catch (InterruptedException e) {
      log.warn(
          "Failure to index database text, proceeding with Lucene Attachment indexing : {}",
          e.getMessage());
    }

    log.info("Deleting all indices from Lucene FileStore index");
    try {
      indexer.init(true);
    } catch (IOException e) {
      log.error(
          "failure to delete Lucene FileStore index. Exiting without reindexing : {}",
          e.getMessage());
      return;
    }

    log.info("Re-indexing File Store");
    try {
      indexer.indexFileStore(false);
    } catch (Exception e) {
      log.error("failure to completely index FileStore - " + e.getMessage(), e);
    } catch (NoClassDefFoundError noClass) {
      // embedded images in RTFs need org/apache/jempbox/xmp/XMPMetadata - there may be other
      // classes as well
      log.error("Tika ParserImplementation class not found - " + noClass.getMessage(), noClass);
    }
    log.info("Lucene re-indexing complete");
  }

  /*
   * =========================
   *   for testing
   * =========================
   */

  protected void setIndexOnStartUp(String indexOnStartUp) {
    this.indexOnstartup = indexOnStartUp;
  }

  protected void setTextDao(TextSearchDao textDao) {
    this.textDao = textDao;
  }

  protected void setIndexer(IFileIndexer indexer) {
    this.indexer = indexer;
  }
}
