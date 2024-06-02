package com.researchspace.search.impl;

import static com.axiope.search.SearchConstants.RECORDS_SEARCH_OPTION;
import static com.researchspace.model.record.TestFactory.createNRecords;
import static org.junit.Assert.*;

import java.util.Collections;
import java.util.Set;
import org.apache.lucene.search.BooleanQuery;
import org.hamcrest.collection.IsIterableContainingInAnyOrder;
import org.junit.Test;

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
    assertTrue(luceneCfg.getSelectedRecordIds().isEmpty());

    mutableCfg.setOptions(
        new String[] {RECORDS_SEARCH_OPTION, RECORDS_SEARCH_OPTION, RECORDS_SEARCH_OPTION});
    mutableCfg.setTerms(new String[] {"FL159, NB160", "SD161 ,SD162", "NB163;FL159"});
    luceneCfg = new LuceneSrchCfg(mutableCfg, termListFactory);

    Set<Long> records = luceneCfg.getSelectedRecordIds();
    assertThat(
        records, IsIterableContainingInAnyOrder.containsInAnyOrder(159L, 160L, 161L, 162L, 163L));
    assertEquals(5, records.size()); // duplicate entries removed
  }
}
