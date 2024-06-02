package com.researchspace.service.impl;

import static org.mockito.Mockito.verify;

import com.axiope.search.IFileIndexer;
import com.researchspace.dao.TextSearchDao;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class LuceneSearchIndexInitialisorTest {

  public @Rule MockitoRule mockito = MockitoJUnit.rule();
  private @Mock TextSearchDao searchDao;
  private @Mock IFileIndexer indexer;
  private LuceneSearchIndexInitialisor luceneInit;

  @Before
  public void setUp() throws Exception {
    luceneInit = new LuceneSearchIndexInitialisor();
    luceneInit.setIndexer(indexer);
    luceneInit.setTextDao(searchDao);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void onInitialAppDeploymentInitialisesIndex() throws IOException {
    luceneInit.onInitialAppDeployment();
    verify(indexer).init(true);
  }

  @Test
  public void onAppVersionUpdateDoesntInitIndex() throws IOException {
    luceneInit.onAppVersionUpdate();
    verify(indexer, Mockito.never()).init(true);
  }

  @Test
  public void testOnAppStartupReinitNoReindex() throws IOException {
    luceneInit.setIndexOnStartUp("false");
    luceneInit.onAppStartup(null);
    verifyNoInitialisation();
  }

  @Test
  public void testOnAppStartupReinitReindex() throws IOException, InterruptedException {
    luceneInit.setIndexOnStartUp("true");
    luceneInit.onAppStartup(null);
    verify(indexer).init(true);
    verify(searchDao).indexText();
  }

  private void verifyNoInitialisation() throws IOException {
    verify(indexer, Mockito.never()).init(true);
    verify(indexer, Mockito.never()).init();
  }
}
