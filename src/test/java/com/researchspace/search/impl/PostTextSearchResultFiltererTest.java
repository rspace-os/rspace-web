package com.researchspace.search.impl;

import static com.researchspace.model.record.TestFactory.createNRecords;
import static org.junit.Assert.assertEquals;

import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.TestFactory;
import java.util.List;
import org.apache.lucene.search.BooleanQuery;
import org.junit.Before;
import org.junit.Test;

public class PostTextSearchResultFiltererTest extends LuceneSrchCfgTestBase {

  private final int ORIGINAL_SIZE = 5;
  private PostTextSearchResultFilterer filterer;
  private List<BaseRecord> toFilter;

  @Before
  public void setUp() {
    toFilter = TestFactory.createNRecords(ORIGINAL_SIZE);
    filterer = new PostTextSearchResultFilterer(toFilter, luceneCfg);
  }

  @Test
  public void filterByDeleted() {
    assertEquals(ORIGINAL_SIZE, filterer.filterAll().size());

    toFilter.get(0).setRecordDeleted(true);
    assertEquals(ORIGINAL_SIZE - 1, filterer.filterAll().size());
  }

  @Test
  public void filterAllAppliesConditionalSharedRecordsFilter() {
    // this will ensure filter is applied; see RSPAC
    List<BaseRecord> bigListOfTerms = createNRecords(BooleanQuery.getMaxClauseCount() + 1);
    // big list of terms does not include target record
    mutableCfg.setRecordFilterList(bigListOfTerms);
    assertEquals(0, filterer.filterAll().size());

    // big list of terms now includes target record
    bigListOfTerms.add(toFilter.get(0));
    assertEquals(1, filterer.filterAll().size());
  }

  @Test
  public void filterAllAppliesConditionalNotebookFilter() {
    final long matchingId = -5L;
    setupNotebookParent(toFilter, matchingId);
    assertEquals(ORIGINAL_SIZE, filterer.filterAll().size());

    final long nonMatchingId = -4L;
    mutableCfg.setFolderId(nonMatchingId);
    assertEquals(ORIGINAL_SIZE, filterer.filterAll().size());
    mutableCfg.setFolderId(matchingId);
    assertEquals(ORIGINAL_SIZE, filterer.filterAll().size());
    // id has to match search term, and filter must be configured, in order to be
    // applied
    mutableCfg.setNotebookFilter(true);
    assertEquals(1, filterer.filterAll().size());
  }

  private void setupNotebookParent(List<BaseRecord> toFilter, final long matchingId) {
    Notebook parent = TestFactory.createANotebook("user", toFilter.get(1).getOwner());
    parent.setId(matchingId);
    parent.addChild(toFilter.get(1), toFilter.get(1).getOwner());
  }
}
