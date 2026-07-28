package com.researchspace.service;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.Assert.assertEquals;

import com.axiope.search.SearchManager;
import com.researchspace.Constants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.User;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import org.apache.lucene.queryparser.classic.ParseException;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.junit.After;
import org.springframework.beans.factory.annotation.Autowired;

/** Base class with common utility functions for search */
public class SearchSpringTestBase extends SpringTransactionalTest {

  @Autowired protected SearchManager searchMgr;

  protected User user;

  protected void assertNHits(WorkspaceListingConfig cfg, final int expectedHitCount, User user)
      throws IOException, ParseException {
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(cfg, user);
    assertEquals(expectedHitCount, results.getHits().intValue());
  }

  protected SearchSession getFullTextSession() {
    return Search.session(sessionFactory.getCurrentSession());
  }

  protected void setupRandomPIUser() throws IllegalAddChildOperation {
    user = createAndSaveUserIfNotExists("PI" + getRandomName(8), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(user);
  }

  protected void setupRandomUser() throws IllegalAddChildOperation {
    user = createAndSaveUserIfNotExists("any" + getRandomName(8));
    initialiseContentWithEmptyContent(user);
  }

  @After
  public void tearDown() throws IOException {
    // make sure index is cleared for each test.
    SearchSession searchSession = Search.session(sessionFactory.getCurrentSession());
    searchSession.workspace(BaseRecord.class, StructuredDocument.class, Sample.class).purge();
    searchSession
        .workspace(BaseRecord.class, StructuredDocument.class, Sample.class)
        .mergeSegments();
    searchSession.workspace(BaseRecord.class, StructuredDocument.class, Sample.class).flush();
    searchSession.workspace(BaseRecord.class, StructuredDocument.class, Sample.class).refresh();
    flushToSearchIndices();
    fileIndexer.close();
  }
}
