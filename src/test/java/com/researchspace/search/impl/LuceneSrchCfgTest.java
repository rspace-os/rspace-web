package com.researchspace.search.impl;

import static com.axiope.search.SearchConstants.RECORDS_SEARCH_OPTION;
import static com.researchspace.testutils.TestFactory.createNRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.lucene.search.BooleanQuery;
import org.junit.jupiter.api.Test;

public class LuceneSrchCfgTest extends LuceneSrchCfgTestBase {

  @Test
  public void isRecordFilterListUsableInLucene() {
    mutableCfg.setRecordFilterList(null);
    assertFalse(luceneCfg.isRecordFilterListUsableInLucene());

    mutableCfg.setRecordFilterList(Collections.emptyList());
    assertFalse(luceneCfg.isRecordFilterListUsableInLucene());

    mutableCfg.setRecordFilterList(createNRecords(1));
    assertTrue(luceneCfg.isRecordFilterListUsableInLucene());

    mutableCfg.setRecordFilterList(createNRecords(BooleanQuery.getMaxClauseCount()));
    assertTrue(luceneCfg.isRecordFilterListUsableInLucene());

    mutableCfg.setRecordFilterList(createNRecords(BooleanQuery.getMaxClauseCount() + 1));
    assertFalse(luceneCfg.isRecordFilterListUsableInLucene());
  }

  @Test
  public void testGettingSelectedRecordId() {
    assertThat(luceneCfg.getSelectedRecordIds()).isEmpty();

    mutableCfg.setOptions(
        new String[] {RECORDS_SEARCH_OPTION, RECORDS_SEARCH_OPTION, RECORDS_SEARCH_OPTION});
    mutableCfg.setTerms(new String[] {"FL159, NB160", "SD161 ,SD162", "NB163;FL159"});
    luceneCfg = new LuceneSrchCfg(mutableCfg, termListFactory);

    Set<Long> records = luceneCfg.getSelectedRecordIds();
    assertThat(records).hasSize(5);
    assertThat(records).containsAll(List.of(159L, 160L, 161L, 162L, 163L));
    assertThat(records).hasSize(5); // duplicate entries removed
  }
}
