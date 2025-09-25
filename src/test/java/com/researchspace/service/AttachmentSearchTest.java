package com.researchspace.service;

import static com.axiope.search.SearchConstants.ATTACHMENT_SEARCH_OPTION;
import static com.axiope.search.SearchConstants.FULL_TEXT_SEARCH_OPTION;
import static com.researchspace.testutils.SearchTestUtils.createAdvSearchCfg;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.search.impl.FileIndexSearcher;
import com.researchspace.search.impl.LuceneSearchStrategy;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.beans.factory.annotation.Autowired;

public class AttachmentSearchTest extends SearchSpringTestBase {

  public @Rule TemporaryFolder tempIndexFolder = new TemporaryFolder();
  @Autowired FileIndexSearcher searcher;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    fileIndexer.setIndexFolderDirectly(tempIndexFolder.getRoot());

    LuceneSearchStrategy searchSTrategy = new LuceneSearchStrategy();
    searchSTrategy.setIndexFolderDirectly(tempIndexFolder.getRoot());
    searcher.setFileSearchStrategy(searchSTrategy);

    initialiseFileIndexer();
  }

  @After
  public void tearDown() throws IOException {
    super.tearDown();
  }

  @Test
  public void attachmentSearchPartOfFullTextSearchTest() throws URISyntaxException, IOException {
    setupRandomPIUser();
    logoutAndLoginAs(user);

    saveTextFileInGallery(user);
    flushToSearchIndices();

    WorkspaceListingConfig config =
        createAdvSearchCfg(new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"people"});

    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());
  }

  @Test
  public void attachmentSearchTest()
      throws IllegalAddChildOperation, IOException, URISyntaxException {

    // create user and index test file
    User user = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user);
    EcatDocumentFile textFile = saveTextFileInGallery(user);
    flushToSearchIndices();

    // prepare search options
    String[] options = {ATTACHMENT_SEARCH_OPTION};
    String[] terms = {"people"};
    PaginationCriteria<BaseRecord> pg = PaginationCriteria.createDefaultForClass(BaseRecord.class);
    WorkspaceListingConfig input = new WorkspaceListingConfig(pg, options, terms, -1L, false);

    // run the search
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(input, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());
    assertEquals(textFile.getGlobalIdentifier(), results.getResults().get(0).getGlobalIdentifier());
    assertEquals(1, ((EcatMediaFile) results.getResults().get(0)).getVersion());

    // upload new version of file, and index
    updateFileAttachmentInGallery(textFile.getId(), user);
    flushToSearchIndices();

    // try searching again, confirm the file is still findable (RSDEV-755)
    results = searchMgr.searchWorkspaceRecords(input, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());
    assertEquals(textFile.getGlobalIdentifier(), results.getResults().get(0).getGlobalIdentifier());
    assertEquals(2, ((EcatMediaFile) results.getResults().get(0)).getVersion());
  }

  // saves an example file in filsestore and DB
  private EcatDocumentFile saveTextFileInGallery(User user) throws URISyntaxException, IOException {
    InputStream is =
        SearchManagerTest.class.getClassLoader().getResourceAsStream("TestResources/genFilesi.txt");
    EcatDocumentFile doc = mediaMgr.saveNewDocument("genFilesi.txt", is, user, null, null);
    return (EcatDocumentFile) recordMgr.save(doc, user);
  }
}
