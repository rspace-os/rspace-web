package com.researchspace.service;

import static com.axiope.search.SearchConstants.ATTACHMENT_SEARCH_OPTION;
import static com.axiope.search.SearchConstants.FULL_TEXT_SEARCH_OPTION;
import static com.axiope.search.SearchConstants.NAME_SEARCH_OPTION;
import static com.axiope.search.SearchConstants.OWNER_SEARCH_OPTION;
import static com.researchspace.testutils.SearchTestUtils.createAdvSearchCfg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.SearchOperator;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.search.impl.FileIndexSearcher;
import com.researchspace.search.impl.LuceneSearchStrategy;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

public class AttachmentSearchTest extends SearchSpringTestBase {

  @TempDir public File tempIndexFolder;
  @Autowired FileIndexSearcher searcher;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    fileIndexer.setIndexFolderDirectly(tempIndexFolder);

    LuceneSearchStrategy searchSTrategy = new LuceneSearchStrategy();
    searchSTrategy.setIndexFolderDirectly(tempIndexFolder);
    searcher.setFileSearchStrategy(searchSTrategy);

    initialiseFileIndexer();
  }

  @AfterEach
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

  @Test
  public void advancedAndSearchFiltersAttachmentMetadata() throws Exception {
    setupRandomPIUser();
    logoutAndLoginAs(user);
    StructuredDocument target = createBasicDocumentInRootFolderWithText(user, "rsdev1470needle");
    target.setName("RSDEV1470Target");
    recordMgr.save(target, user);
    StructuredDocument excluded = createBasicDocumentInRootFolderWithText(user, "Other content");
    excluded.setName("RSDEV1470Excluded");
    EcatDocumentFile unrelated = saveNeedleFile("RSDEV1470Unrelated.txt");
    EcatDocumentFile matching = saveNeedleFile("RSDEV1470Target.txt");
    excluded
        .getFields()
        .get(0)
        .setFieldData(
            "<a class=\"attachmentLinked\" id=\"attachOnText_"
                + unrelated.getId()
                + "\" href=\"/Streamfile/"
                + unrelated.getId()
                + "\">Attachment</a>");
    recordMgr.save(excluded, user);
    flushToSearchIndices();

    WorkspaceListingConfig config =
        createAdvSearchCfg(
            new String[] {NAME_SEARCH_OPTION, OWNER_SEARCH_OPTION},
            new String[] {"RSDEV1470Target*", user.getUsername()},
            SearchOperator.AND);
    assertThat(searchMgr.searchWorkspaceRecords(config, user).getResults())
        .extracting(BaseRecord::getId)
        .containsExactlyInAnyOrder(target.getId(), matching.getId());

    config =
        createAdvSearchCfg(
            new String[] {NAME_SEARCH_OPTION, OWNER_SEARCH_OPTION, FULL_TEXT_SEARCH_OPTION},
            new String[] {"RSDEV1470Target*", user.getUsername(), "rsdev1470needle"},
            SearchOperator.AND);
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(config, user);
    assertThat(results.getResults())
        .extracting(BaseRecord::getId)
        .containsExactlyInAnyOrder(target.getId(), matching.getId());
    assertEquals(2, results.getTotalHits().intValue());
    config.getPgCrit().setResultsPerPage(2);
    config.getPgCrit().setOrderBy("name");
    config.getPgCrit().setSortOrder(SortOrder.ASC);
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertThat(results.getResults())
        .extracting(BaseRecord::getId)
        .containsExactly(target.getId(), matching.getId());
    assertEquals(2, results.getTotalHits().intValue());

    config =
        createAdvSearchCfg(
            new String[] {NAME_SEARCH_OPTION, OWNER_SEARCH_OPTION, FULL_TEXT_SEARCH_OPTION},
            new String[] {"RSDEV1470Impossible", user.getUsername(), "rsdev1470needle"},
            SearchOperator.AND);
    assertThat(searchMgr.searchWorkspaceRecords(config, user).getResults()).isEmpty();

    config =
        createAdvSearchCfg(
            new String[] {NAME_SEARCH_OPTION, OWNER_SEARCH_OPTION, FULL_TEXT_SEARCH_OPTION},
            new String[] {"RSDEV1470Target*", user.getUsername(), "rsdev1470needle"},
            SearchOperator.OR);
    assertThat(searchMgr.searchWorkspaceRecords(config, user).getResults())
        .extracting(BaseRecord::getId)
        .contains(target.getId(), excluded.getId(), matching.getId(), unrelated.getId());

    config =
        createAdvSearchCfg(
            new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"rsdev1470needle"});
    assertThat(searchMgr.searchWorkspaceRecords(config, user).getResults())
        .extracting(BaseRecord::getId)
        .containsExactlyInAnyOrder(target.getId(), matching.getId(), unrelated.getId());
  }

  private EcatDocumentFile saveNeedleFile(String name) throws IOException {
    try (InputStream content =
        new ByteArrayInputStream("rsdev1470needle".getBytes(StandardCharsets.UTF_8))) {
      return (EcatDocumentFile)
          recordMgr.save(mediaMgr.saveNewDocument(name, content, user, null, null), user);
    }
  }

  // saves an example file in filsestore and DB
  private EcatDocumentFile saveTextFileInGallery(User user) throws URISyntaxException, IOException {
    InputStream is =
        SearchManagerTest.class.getClassLoader().getResourceAsStream("TestResources/genFilesi.txt");
    EcatDocumentFile doc = mediaMgr.saveNewDocument("genFilesi.txt", is, user, null, null);
    return (EcatDocumentFile) recordMgr.save(doc, user);
  }
}
