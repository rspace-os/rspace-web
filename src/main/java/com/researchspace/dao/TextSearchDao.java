package com.researchspace.dao;

import com.axiope.search.InventorySearchConfig;
import com.axiope.search.SearchConfig;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.record.BaseRecord;
import java.io.IOException;
import java.util.List;

public interface TextSearchDao {

  /** Only called from test code, is this needed? */
  @SuppressWarnings("rawtypes")
  List searchText(String flds[], String match, Class<?> persistentClass);

  /**
   * Explicitly rebuilds the full text index
   *
   * @throws InterruptedException
   */
  void indexText() throws InterruptedException;

  /**
   * Search through base records.
   *
   * @param searchCfg
   * @return ISearchResults<BaseRecord>
   */
  ISearchResults<BaseRecord> getSearchedElnResults(SearchConfig searchCfg) throws IOException;

  /** Search through inventory records. */
  ISearchResults<InventoryRecord> getSearchedInventoryResults(InventorySearchConfig searchConfig);
}
