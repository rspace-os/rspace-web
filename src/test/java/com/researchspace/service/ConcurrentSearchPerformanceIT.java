package com.researchspace.service;

import static com.researchspace.testutils.SearchTestUtils.createAdvSearchCfg;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.axiope.search.SearchConstants;
import com.axiope.search.SearchManager;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.User;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.search.mapper.orm.Search;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;

@EnabledIfSystemProperty(named = "performanceTests", matches = "true")
public class ConcurrentSearchPerformanceIT extends RealTransactionSpringTestBase {

  private @Autowired SearchManager searchManager;

  @Test
  public void concurrentSearchesSeeCommittedFixtures() throws Exception {
    int searchCount = 2;
    User[] users = new User[searchCount];
    String[] terms = new String[searchCount];
    for (int i = 0; i < searchCount; i++) {
      users[i] = doCreateAndInitUser(getRandomAlphabeticString("search"));
      terms[i] = CoreTestUtils.getRandomName(20);
      createBasicDocumentInRootFolderWithText(users[i], terms[i]);
    }
    doInTransaction(
        () ->
            Search.session(sessionFactory.getCurrentSession())
                .workspace(BaseRecord.class, StructuredDocument.class)
                .refresh());

    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(searchCount);
    List<Future<Void>> searches = new ArrayList<>();
    try {
      for (int i = 0; i < searchCount; i++) {
        User user = users[i];
        String term = terms[i];
        searches.add(
            executor.submit(
                () -> {
                  start.await();
                  WorkspaceListingConfig config =
                      createAdvSearchCfg(
                          new String[] {SearchConstants.FULL_TEXT_SEARCH_OPTION},
                          new String[] {term});
                  ISearchResults<BaseRecord> results =
                      searchManager.searchWorkspaceRecords(config, user);
                  assertEquals(1, results.getHits().intValue());
                  return null;
                }));
      }
      start.countDown();
      for (Future<Void> search : searches) {
        search.get(30, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }
  }
}
