package com.researchspace.service;

import static com.researchspace.testutils.SearchTestUtils.createAdvSearchCfg;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.axiope.search.FileSearchResult;
import com.axiope.search.IFileIndexer;
import com.axiope.search.SearchConstants;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.testutil.RandomTextFileGenerator;
import com.researchspace.core.testutil.RandomTextFileGenerator.FileSearchTerms;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.hibernate.FullTextSearcherImpl;
import com.researchspace.model.User;
import com.researchspace.model.dtos.SearchOperator;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.search.impl.FileIndexSearcher;
import com.researchspace.search.impl.FileIndexer;
import com.researchspace.search.impl.LuceneSearchStrategy;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.time.StopWatch;
import org.apache.lucene.queryparser.classic.ParseException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@RunWith(ConditionalTestRunner.class)
public class SearchPerformanceTest extends SearchSpringTestBase {

  Logger log = LoggerFactory.getLogger(SearchPerformanceTest.class);

  IFileIndexer fileIndexer;
  @Rule public TemporaryFolder randomFilefolder = new TemporaryFolder();
  @Rule public TemporaryFolder indexfolder = new TemporaryFolder();
  @Autowired FileIndexSearcher fileIndexSearcher;

  @Autowired FullTextSearcherImpl fullTextSearcher;
  User anyUser = null;

  @Before
  public void setUp() throws Exception {
    anyUser = TestFactory.createAnyUser("any");
    fileIndexer = new FileIndexer();
    fileIndexer.setIndexFolderDirectly(indexfolder.getRoot());
    fileIndexer.init(true);
    getTargetObject(fileIndexSearcher.getFileSearchStrategy(), LuceneSearchStrategy.class)
        .setIndexFolderDirectly(indexfolder.getRoot());
    fileIndexSearcher.getFileSearchStrategy().setDefaultReturnDocs(2000);
  }

  class Searcher implements Runnable {

    Searcher(User user, String term) {
      super();
      this.user = user;
      this.term = term;
    }

    User user;
    String term;

    @Override
    public void run() {
      WorkspaceListingConfig cfg =
          createAdvSearchCfg(
              new String[] {SearchConstants.FULL_TEXT_SEARCH_OPTION},
              new String[] {term},
              SearchOperator.AND);

      try {
        assertNHits(cfg, 1, user);
      } catch (IOException | ParseException e) {
        e.printStackTrace();
      }
    }
  }

  @Test
  @Ignore
  public void testConcurrentSearch() throws InterruptedException {
    int numUsers = 2;
    User[] users = new User[numUsers];
    StructuredDocument[] docs = new StructuredDocument[numUsers];
    String[] texts = new String[numUsers];
    Searcher[] searchers = new Searcher[numUsers];
    for (int i = 0; i < numUsers; i++) {
      texts[i] = getRandomAlphabeticString("");
    }
    for (int i = 0; i < numUsers; i++) {
      users[i] = createAndSaveRandomUser();
      initialiseContentWithEmptyContent(users[i]);
      docs[i] = createBasicDocumentInRootFolderWithText(users[i], texts[i]);
      searchers[i] = new Searcher(users[i], texts[i]);
    }
    flushToSearchIndices();

    // flushDatabaseState();
    for (Searcher searcher : searchers) {
      Thread t = new Thread(searcher);
      t.start();
      t.join(); // wait for thread to finish before exiting
      //	searcher.run();
    }
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testPerformance()
      throws IllegalAddChildOperation, InterruptedException, IOException, ParseException {
    final int numUsers = 50;
    final int numDocsPerUser = 10;
    String[] srchTerms = new String[numUsers];
    User[] users = new User[numUsers];
    StopWatch sw = new StopWatch();
    sw.start();
    // create users, with 1 record, each with 1 record with unique results
    for (int i = 0; i < numUsers; i++) {
      String uname = CoreTestUtils.getRandomName(numDocsPerUser);
      User u = createAndSaveUserIfNotExists(uname);
      users[i] = u;

      initialiseContentWithEmptyContent(u);
      // unique string
      String unique = CoreTestUtils.getRandomName(numDocsPerUser);
      String text = " common " + unique + " yz ";
      srchTerms[i] = unique;
      for (int j = 0; j < numDocsPerUser; j++) {
        // StructuredDocument sd =
        createBasicDocumentInRootFolderWithText(u, text);
        Thread.sleep(1);
      }

      if (i % numDocsPerUser == 0) {
        log.info(i + " setup  in  " + sw.getTime() + "ms");
        sessionFactory.getCurrentSession().flush();
      }
    }
    sw.stop();
    log.info(" setup cmpleted in  " + sw.getTime() + "ms");
    sw.reset();
    flushToSearchIndices();
    RSpaceTestUtils.logoutCurrUserAndLoginAs(users[0].getUsername(), TESTPASSWD);
    sw.start();
    // search for new string
    WorkspaceListingConfig config =
        createAdvSearchCfg(
            new String[] {SearchConstants.FULL_TEXT_SEARCH_OPTION}, new String[] {srchTerms[0]});
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(config, users[0]);

    sw.stop();
    long uniqueTime = sw.getTime();
    System.out.println(" search for unique string took " + uniqueTime);
    assertEquals(numDocsPerUser, results.getResults().size());

    sw.reset();

    // now do search which will get all records before perm filterint
    String COMMON_TERM = "common";
    config =
        createAdvSearchCfg(
            new String[] {SearchConstants.FULL_TEXT_SEARCH_OPTION}, new String[] {COMMON_TERM});

    sw.start();
    results = searchMgr.searchWorkspaceRecords(config, users[0]);
    sw.stop();
    assertEquals(numDocsPerUser, results.getResults().size());
    long commonTime = sw.getTime();
    System.out.println(" search for common string = " + commonTime + " ms ");
    // test that search for common term is roughly comparable with unique
    // term
    assertTrue(commonTime < uniqueTime * 2);
    sw.reset();
    // now do advanced search
    config =
        createAdvSearchCfg(
            new String[] {
              SearchConstants.FULL_TEXT_SEARCH_OPTION, SearchConstants.FULL_TEXT_SEARCH_OPTION
            },
            new String[] {COMMON_TERM, srchTerms[0]});

    sw.start();
    results = searchMgr.searchWorkspaceRecords(config, users[0]);
    sw.stop();
    System.out.println("advanced search = " + sw.getTime() + " ms ");
    assertEquals(numDocsPerUser, results.getResults().size());
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testIndexingSingleThread() throws IOException, ParseException {
    RandomTextFileGenerator tfgg = new RandomTextFileGenerator();
    System.out.println("generating setup");
    List<FileSearchTerms> created = tfgg.generate(randomFilefolder.getRoot(), 100, 100);
    System.out.println("file generation completed");
    assertEquals(100, created.size());
    for (FileSearchTerms toIndex : created) {
      System.out.println("indexing " + toIndex.getFile());
      fileIndexer.indexFile(toIndex.getFile());
    }

    for (FileSearchTerms term : created) {
      List<FileSearchResult> results =
          fileIndexSearcher.getFileSearchStrategy().searchFiles(term.getTerms()[0], anyUser);
      assertTrue("No results for " + term, results.size() > 0);
      System.out.println("searching " + term.getFile() + ", found " + results.size() + " hits");
    }
  }

  // this indexes, then searches
  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testIndexingMultiThread()
      throws IOException,
          ParseException,
          InterruptedException,
          ExecutionException,
          TimeoutException {
    RandomTextFileGenerator tfgg = new RandomTextFileGenerator();
    List<FileSearchTerms> created = tfgg.generate(randomFilefolder.getRoot(), 100, 50);
    log.info("file generation completed");
    // each tread will index one file at a time from the queue
    final Queue<FileSearchTerms> toIndexqueue = new ConcurrentLinkedQueue<>();
    toIndexqueue.addAll(created);

    final int threadCount = 50;
    ExecutorService service = Executors.newFixedThreadPool(threadCount);
    List<Future<?>> submittedAll = new ArrayList<Future<?>>();
    for (FileSearchTerms toIndex : created) {
      // submit indexing in multithreads
      Future<?> submitted =
          service.submit(
              () -> {
                if (!toIndexqueue.isEmpty()) {
                  FileSearchTerms toIndex2 = toIndexqueue.poll();
                  try {
                    fileIndexer.indexFile(toIndex2.getFile());
                    System.out.println(
                        "indexing "
                            + toIndex2.getFile()
                            + " from thread "
                            + Thread.currentThread().getName());
                  } catch (IOException e) {
                    e.printStackTrace();
                  }
                }
              });
      submittedAll.add(submitted);
    }
    // wait for all indexing threads to finish
    for (Future<?> job : submittedAll) {
      job.get(300, TimeUnit.SECONDS);
      System.out.println("job done");
    }
    log.info("searching sequentially");
    for (FileSearchTerms term : created) {
      assertSearchOK(term);
    }
  }

  private void assertSearchOK(FileSearchTerms term) throws IOException, ParseException {
    List<FileSearchResult> results =
        fileIndexSearcher.getFileSearchStrategy().searchFiles(term.getTerms()[0], anyUser);
    assertTrue("No results for " + term, results.size() > 0);
    if (!assertResultsContainsFile(results, term)) {
      fail(
          "Search hits for term ("
              + ArrayUtils.toString(term.getTerms())
              + ") didn't contain original file ("
              + term.getFile()
              + ")");
    }
  }

  // this indexes, and searches at the same time
  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testIndexingAndSearchingAtSameTime()
      throws IOException,
          ParseException,
          InterruptedException,
          ExecutionException,
          TimeoutException {
    RandomTextFileGenerator tfgg = new RandomTextFileGenerator();
    List<FileSearchTerms> created = tfgg.generate(randomFilefolder.getRoot(), 100, 200);
    // each tread will index one file at a time from the queue
    final Queue<FileSearchTerms> toIndexqueue = new ConcurrentLinkedQueue<>();
    final BlockingQueue<FileSearchTerms> toSearchQueue = new LinkedBlockingQueue<>();
    toIndexqueue.addAll(created);

    final int threadCount = 50;
    ExecutorService service = Executors.newFixedThreadPool(threadCount);

    for (FileSearchTerms unused : created) {
      // submit indexing in multithreads
      service.submit(
          () -> {
            if (!toIndexqueue.isEmpty()) {
              FileSearchTerms toIndex = toIndexqueue.poll();
              try {
                fileIndexer.indexFile(toIndex.getFile());
                Thread.sleep(25); // slow indexing so that searching
                // will intercalate better
                log.info("indexing " + toIndex);
                toSearchQueue.add(toIndex);
              } catch (IOException | InterruptedException e) {
                e.printStackTrace();
              }
            }
          });
    }
    List<Future<?>> submittedAll = new ArrayList<>();
    // 10 runnables which will consume searching queue
    for (int i = 0; i < 10; i++) {
      // submit searching in multithreads
      Future<?> submitted =
          service.submit(
              () -> {
                FileSearchTerms toSearch = null;
                try {
                  // queue might be waiting for indexing, so wait 5s for
                  // queue to be non-empty
                  while ((toSearch = toSearchQueue.poll(5, TimeUnit.SECONDS)) != null) {
                    try {
                      log.info(
                          "searching "
                              + toSearch.getTerms()[0]
                              + " in "
                              + Thread.currentThread().getName());
                      log.info("current search queue size:  " + toSearchQueue.size());
                      assertSearchOK(toSearch);
                    } catch (IOException | ParseException e) {
                      e.printStackTrace();
                    }
                  }
                  log.info("queue empty timeout for thread: " + Thread.currentThread().getName());
                } catch (InterruptedException e1) {
                  e1.printStackTrace();
                }
              });
      submittedAll.add(submitted);
    }
    // wait for all searching threads to finish before finishing test
    for (Future<?> job : submittedAll) {
      job.get(3000, TimeUnit.SECONDS);
    }
  }

  private boolean assertResultsContainsFile(List<FileSearchResult> results, FileSearchTerms term) {
    return results.stream()
        .map(FileSearchResult::getFileName)
        .anyMatch(term.getFile().getName()::equals);
  }
}
