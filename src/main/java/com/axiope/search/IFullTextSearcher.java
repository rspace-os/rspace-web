package com.axiope.search;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecordAdaptable;
import java.io.IOException;

public interface IFullTextSearcher {

  /** Multiple query. */
  int ALL_LUCENE_SEARCH_STRATEGY = 1;

  /** Multiple query. */
  int ADVANCED_LUCENE_SEARCH_STRATEGY = 2;

  /** Simple query. */
  int SINGLE_LUCENE_SEARCH_STRATEGY = 3;

  /** */
  int DEFAULT_LUCENE_SEARCH_STRATEGY = 10;

  /**
   * Optional setter for running Full text search when we want to adapt returned objects to their
   * enclosing records.
   *
   * @param baseRecordAdapter
   */
  void setBaseRecordAdaptable(BaseRecordAdaptable baseRecordAdapter);

  /**
   * Main full text search method. This method is used in the simple search (options FullText search
   * or Tag search) and in the advanced search.
   *
   * @param srchConfig The search term An integer specifying the type of query (wildcard, in quotes,
   *     etc) The starting page number The current page size.
   * @return Am {@link ISearchResults} or <code>null</code> if no hits.
   * @throws SearchQueryParseException
   */
  ISearchResults<BaseRecord> getSearchedElnRecords(SearchConfig srchConfig) throws IOException;

  ISearchResults<InventoryRecord> getSearchedInventoryRecords(InventorySearchConfig searchConfig);
}
