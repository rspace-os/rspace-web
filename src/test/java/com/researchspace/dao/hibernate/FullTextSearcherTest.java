package com.researchspace.dao.hibernate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.axiope.search.IFullTextSearcher;
import com.axiope.search.InventorySearchConfig;
import com.axiope.search.InventorySearchConfig.InventorySearchType;
import com.axiope.search.SearchConfig;
import com.axiope.search.SearchConstants;
import com.axiope.search.SearchUtils;
import com.axiope.search.WorkspaceSearchConfig;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleNote;
import com.researchspace.core.util.IPagination;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.SortOrder;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.dao.BaseDaoTestCase;
import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.User;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.TestFactory;
import com.researchspace.search.impl.LuceneSearchTermListFactory;
import com.researchspace.search.impl.LuceneSrchCfg;
import com.researchspace.testutils.SearchTestUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class FullTextSearcherTest extends BaseDaoTestCase {

  @Autowired private FullTextSearcherImpl fts;
  @Autowired private LuceneSearchTermListFactory termListFactory;

  @Test
  public void testRepaginationOfResults() {
    User u = TestFactory.createAnyUser("any");
    SearchConfig searchConfig = new WorkspaceSearchConfig(u);
    searchConfig.setOptions(new String[] {SearchConstants.FULL_TEXT_SEARCH_OPTION});
    searchConfig.setTerms(new String[] {"any"});
    LuceneSrchCfg cfg = new LuceneSrchCfg(searchConfig, termListFactory);

    int foldersNumber = IPagination.DEFAULT_RESULTS_PERPAGE - 1;
    List<BaseRecord> res = createNFolders(foldersNumber, u);
    ISearchResults<BaseRecord> srchRes = new SearchResultsImpl<BaseRecord>(res, 0, foldersNumber);
    ISearchResults<BaseRecord> repaginated = fts.repaginateResults(cfg, srchRes);
    assertEquals(foldersNumber, repaginated.getTotalHits().longValue());
    assertEquals(0L, repaginated.getPageNumber().longValue());
    assertEquals(foldersNumber, repaginated.getResults().size());

    foldersNumber = IPagination.DEFAULT_RESULTS_PERPAGE;
    res = createNFolders(foldersNumber, u);
    srchRes = new SearchResultsImpl<BaseRecord>(res, 0, foldersNumber);

    repaginated = fts.repaginateResults(cfg, srchRes);
    assertEquals(foldersNumber, repaginated.getTotalHits().longValue());
    assertEquals(0L, repaginated.getPageNumber().longValue());
    assertEquals(foldersNumber, repaginated.getResults().size());

    foldersNumber = IPagination.DEFAULT_RESULTS_PERPAGE + 1;
    res = createNFolders(foldersNumber, u);
    srchRes = new SearchResultsImpl<BaseRecord>(res, 0, foldersNumber);
    repaginated = fts.repaginateResults(cfg, srchRes);
    assertEquals(foldersNumber, repaginated.getTotalHits().longValue());
    assertEquals(0L, repaginated.getPageNumber().longValue());
    assertEquals(IPagination.DEFAULT_RESULTS_PERPAGE, repaginated.getResults().size());

    // second page
    srchRes = new SearchResultsImpl<BaseRecord>(res, 1, foldersNumber);
    cfg.getPaginationCriteria().setPageNumber(1L);
    repaginated = fts.repaginateResults(cfg, srchRes);
    assertEquals(foldersNumber, repaginated.getTotalHits().longValue());
    assertEquals(1L, repaginated.getPageNumber().longValue());
    assertEquals(1L, repaginated.getResults().size());
  }

  private List<BaseRecord> createNFolders(int numFoldersToCreate, User creator) {
    List<BaseRecord> res = new ArrayList<BaseRecord>();
    for (int i = 0; i < numFoldersToCreate; i++) {
      res.add(TestFactory.createAFolder(i + "", creator));
    }
    return res;
  }

  @Test
  public void testListRawResultsFilteredByUser() {
    // set up 2 users each with a document with the same term
    User pi = createAndSaveAPi();
    User u2 = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(pi, u2);
    logoutAndLoginAs(pi);
    String COMMON_TERM = "common";
    createBasicDocumentInRootFolderWithText(pi, COMMON_TERM);
    logoutAndLoginAs(u2);
    createBasicDocumentInRootFolderWithText(u2, COMMON_TERM);
    flushToSearchIndices();
    // restr
    WorkspaceListingConfig input = SearchTestUtils.createSimpleFullTextSearchCfg(COMMON_TERM);
    SearchConfig searchConfig = new WorkspaceSearchConfig(u2);
    searchConfig.setOptions(input.getSrchOptions());
    searchConfig.setTerms(input.getSrchTerms());
    searchConfig.setUsernameFilter(TransformerUtils.toList(u2.getUsername()));
    LuceneSrchCfg cfg = new LuceneSrchCfg(searchConfig, termListFactory);

    List<IFieldLinkableElement> results = fts.getElnHibernateList(cfg);
    assertEquals(1, results.size());

    searchConfig.setUsernameFilter(TransformerUtils.toList(u2.getUsername(), pi.getUsername()));
    results = fts.getElnHibernateList(cfg);
    assertEquals(2, results.size());

    // now set max results to 1:
    searchConfig.setMaxResults(1);
    results = fts.getElnHibernateList(cfg);
    assertEquals(1, results.size()); // truncated by max results
  }

  @Test
  public void testInventoryRecordQuery() throws IOException, InterruptedException {
    User u = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(u);
    logoutAndLoginAs(u);

    // container named "my container", with tags
    ApiContainer apiContainer = new ApiContainer();
    apiContainer.setName("my container");
    apiContainer.setApiTagInfo("test2");
    ApiContainer topContainer = containerApiMgr.createNewApiContainer(apiContainer, u);

    // three subcontainers, 2nd and third inside top container
    ApiContainer apiSubContainer = new ApiContainer();
    apiSubContainer.setName("my subcontainer1");
    containerApiMgr.createNewApiContainer(apiSubContainer, u);
    apiSubContainer.setName("my subcontainer2");
    apiSubContainer.setParentContainer(topContainer);
    containerApiMgr.createNewApiContainer(apiSubContainer, u);
    apiSubContainer.setName("my subcontainer3");
    containerApiMgr.createNewApiContainer(apiSubContainer, u);

    // sample named "mySample", with extra field
    ApiSampleWithFullSubSamples sample1 = createBasicSampleForUser(u);
    ApiExtraField extraField = new ApiExtraField();
    extraField.setNewFieldRequest(true);
    extraField.setContent("extra field content");
    sample1.getExtraFields().add(extraField);
    sampleApiMgr.updateApiSample(sample1, u);

    // sample named "mySample #2", with tags
    ApiSampleWithFullSubSamples sample2 = createBasicSampleForUser(u);
    sample2.setName("mySample #2");
    sample2.setApiTagInfo("test1, test2, test3");
    sample2.setDescription("desc1");
    sampleApiMgr.updateApiSample(sample2, u);
    ApiSubSample apiSubSample = sample2.getSubSamples().get(0);
    apiSubSample.setApiTagInfo("subsampleTag1, test2, test3");
    subSampleApiMgr.updateApiSubSample(apiSubSample, u);
    subSampleApiMgr.addSubSampleNote(apiSubSample.getId(), new ApiSubSampleNote("note #1"), u);

    flushToSearchIndices();

    InventorySearchConfig searchConfig = new InventorySearchConfig(u);
    searchConfig.setOptions(new String[] {SearchConstants.INVENTORY_SEARCH_OPTION});
    searchConfig.setSearchStrategy(IFullTextSearcher.ALL_LUCENE_SEARCH_STRATEGY);
    searchConfig.setUsernameFilter(TransformerUtils.toList(u.getUsername()));
    searchConfig.getPaginationCriteria().setOrderBy(SearchUtils.ORDER_BY_NAME);

    // no results for "unknown" name
    searchConfig.setTerms(new String[] {"unknown"});
    LuceneSrchCfg cfg = new LuceneSrchCfg(searchConfig, termListFactory);
    List<InventoryRecord> results = fts.getLuceneInventoryQueryList(cfg);
    assertEquals(0, results.size());

    // two samples found for "mysample" name
    searchConfig.setTerms(new String[] {"mysample"});
    cfg = new LuceneSrchCfg(searchConfig, termListFactory);
    results = fts.getLuceneInventoryQueryList(cfg);
    assertEquals(2, results.size());

    // all samples and subsamples found in wildcard search "mys*"
    searchConfig.setTerms(new String[] {"mys*"});
    cfg = new LuceneSrchCfg(searchConfig, termListFactory);
    results = fts.getLuceneInventoryQueryList(cfg);
    assertEquals(4, results.size());
    assertEquals("mySubSample", results.get(0).getName());
    assertEquals("mySample #2", results.get(2).getName());
    assertEquals("mySample", results.get(3).getName());

    // change ordering to name asc
    searchConfig.getPaginationCriteria().setSortOrder(SortOrder.ASC);
    cfg = new LuceneSrchCfg(searchConfig, termListFactory);
    results = fts.getLuceneInventoryQueryList(cfg);
    assertEquals(4, results.size());
    assertEquals("mySample", results.get(0).getName());
    assertEquals("mySample #2", results.get(1).getName());
    assertEquals("mySubSample", results.get(3).getName());

    // change ordering to creation date asc
    searchConfig.getPaginationCriteria().setOrderBy(SearchUtils.ORDER_BY_CREATION_DATE);
    cfg = new LuceneSrchCfg(searchConfig, termListFactory);
    results = fts.getLuceneInventoryQueryList(cfg);
    assertEquals(4, results.size());
    /* the test creates sample and subsample almost at the same time,
     * so any specific order assertion sometimes fails on jenkins */

    // a sample and a subsample found for "test2" tag search
    searchConfig.getPaginationCriteria().setOrderBy(SearchUtils.ORDER_BY_GLOBAL_ID);
    searchConfig.setTerms(new String[] {"test2"});
    cfg = new LuceneSrchCfg(searchConfig, termListFactory);
    results = fts.getLuceneInventoryQueryList(cfg);
    assertEquals(3, results.size());
    assertEquals("my container", results.get(0).getName());
    assertEquals("mySample #2", results.get(1).getName());
    assertEquals("mySubSample", results.get(2).getName());

    // just one result if we limit search to samples
    searchConfig.setSearchType(InventorySearchType.SAMPLE);
    searchConfig.setTerms(new String[] {"test2"});
    cfg = new LuceneSrchCfg(searchConfig, termListFactory);
    results = fts.getLuceneInventoryQueryList(cfg);
    assertEquals(1, results.size());
    assertEquals(sample2.getGlobalId(), results.get(0).getGlobalIdentifier());

    // just one result if we limit search to subsamples
    searchConfig.setSearchType(InventorySearchType.SUBSAMPLE);
    searchConfig.setTerms(
        new String[] {
          "test2",
        });
    cfg = new LuceneSrchCfg(searchConfig, termListFactory);
    results = fts.getLuceneInventoryQueryList(cfg);
    assertEquals(1, results.size());
    assertEquals(apiSubSample.getGlobalId(), results.get(0).getGlobalIdentifier());

    // just one result if we limit search to cotainers
    searchConfig.setSearchType(InventorySearchType.CONTAINER);
    searchConfig.setTerms(new String[] {"test2"});
    cfg = new LuceneSrchCfg(searchConfig, termListFactory);
    results = fts.getLuceneInventoryQueryList(cfg);
    assertEquals(1, results.size());
    assertEquals(topContainer.getGlobalId(), results.get(0).getGlobalIdentifier());

    // one result for "desc1"
    searchConfig.setSearchType(InventorySearchType.ALL);
    searchConfig.setOptions(new String[] {SearchConstants.INVENTORY_SEARCH_OPTION});
    searchConfig.setTerms(new String[] {"desc1"});
    cfg = new LuceneSrchCfg(searchConfig, termListFactory);
    results = fts.getLuceneInventoryQueryList(cfg);
    assertEquals(1, results.size());
    assertEquals(sample2.getId(), results.get(0).getId());

    // one result for "extra" (from extra field content)
    searchConfig.setTerms(new String[] {"extra"});
    cfg = new LuceneSrchCfg(searchConfig, termListFactory);
    results = fts.getLuceneInventoryQueryList(cfg);
    assertEquals(1, results.size());
    assertEquals(sample1.getId(), results.get(0).getId());

    // one result for "note"
    searchConfig.setTerms(new String[] {"note"});
    cfg = new LuceneSrchCfg(searchConfig, termListFactory);
    results = fts.getLuceneInventoryQueryList(cfg);
    assertEquals(1, results.size());
    assertEquals(apiSubSample.getId(), results.get(0).getId());

    // search with parentID
    searchConfig.setOptions(
        new String[] {
          SearchConstants.INVENTORY_SEARCH_OPTION, SearchConstants.INVENTORY_PARENT_ID_OPTION
        });
    searchConfig.setTerms(new String[] {"s*", "" + topContainer.getId()});
    cfg = new LuceneSrchCfg(searchConfig, termListFactory);
    results = fts.getLuceneInventoryQueryList(cfg);
    assertEquals(2, results.size());
    assertEquals("my subcontainer2", results.get(0).getName());
    assertEquals("my subcontainer3", results.get(1).getName());
  }
}
