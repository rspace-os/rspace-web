package com.axiope.service.cfg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.axiope.search.IFileIndexer;
import com.researchspace.files.service.ExternalFileStore;
import com.researchspace.files.service.ExternalFileStoreLocator;
import com.researchspace.files.service.ExternalFileStoreProvider;
import com.researchspace.files.service.FileStore;
import com.researchspace.files.service.egnyte.EgnyteFileStoreAdapter;
import com.researchspace.testutils.EgnyteTestConfig;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

@EgnyteTestConfig
public class EgnyteFileSetupConfigTest extends SpringTransactionalTest {

  @Autowired IFileIndexer indexer;

  @Autowired
  @Qualifier("compositeFileStore")
  private FileStore fileStore;

  @Autowired ExternalFileStore extFileStore;
  @Autowired ExternalFileStoreLocator locator;

  @Test
  public void egnyeFileSTorConfigLoadsNoopIndexer() {
    assertTrue(indexer.isInitialised());
  }

  @Test
  public void egnyteFileStoreConfig() {
    assertEquals(ExternalFileStoreProvider.EGNYTE, locator.getExternalProvider());
    assertTrue(extFileStore instanceof EgnyteFileStoreAdapter);
  }
}
