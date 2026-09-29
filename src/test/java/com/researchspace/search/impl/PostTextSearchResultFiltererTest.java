package com.researchspace.search.impl;

import static com.researchspace.testutils.TestFactory.createNRecords;
import static org.assertj.core.api.Assertions.assertThat;

import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Notebook;
import com.researchspace.testutils.TestFactory;
import java.util.List;
import org.apache.lucene.search.BooleanQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PostTextSearchResultFiltererTest extends LuceneSrchCfgTestBase {

  private final int ORIGINAL_SIZE = 5;
  private PostTextSearchResultFilterer filterer;
  private List<BaseRecord> toFilter;

  @BeforeEach
  public void setUp() {
    toFilter = TestFactory.createNRecords(ORIGINAL_SIZE);
    filterer = new PostTextSearchResultFilterer(toFilter, luceneCfg);
  }

  @Test
  public void filterByDeleted() {
    assertThat(filterer.filterAll()).hasSize(ORIGINAL_SIZE);

    toFilter.get(0).setRecordDeleted(true);
    assertThat(filterer.filterAll()).hasSize(ORIGINAL_SIZE - 1);
  }

  @Test
  public void filterAllAppliesConditionalSharedRecordsFilter() {
    // this will ensure filter is applied; see RSPAC
    List<BaseRecord> bigListOfTerms = createNRecords(BooleanQuery.getMaxClauseCount() + 1);
    // big list of terms does not include target record
    mutableCfg.setRecordFilterList(bigListOfTerms);
    assertThat(filterer.filterAll()).isEmpty();

    // big list of terms now includes target record
    bigListOfTerms.add(toFilter.get(0));
    assertThat(filterer.filterAll()).hasSize(1);
  }

  @Test
  public void filterAllAppliesConditionalNotebookFilter() {
    final long matchingId = -5L;
    setupNotebookParent(toFilter, matchingId);
    assertThat(filterer.filterAll()).hasSize(ORIGINAL_SIZE);

    final long nonMatchingId = -4L;
    mutableCfg.setFolderId(nonMatchingId);
    assertThat(filterer.filterAll()).hasSize(ORIGINAL_SIZE);
    mutableCfg.setFolderId(matchingId);
    assertThat(filterer.filterAll()).hasSize(ORIGINAL_SIZE);
    // id has to match search term, and filter must be configured, in order to be
    // applied
    mutableCfg.setNotebookFilter(true);
    assertThat(filterer.filterAll()).hasSize(1);
  }

  private void setupNotebookParent(List<BaseRecord> toFilter, final long matchingId) {
    Notebook parent = TestFactory.createANotebook("user", toFilter.get(1).getOwner());
    parent.setId(matchingId);
    parent.addChild(toFilter.get(1), toFilter.get(1).getOwner());
  }
}
