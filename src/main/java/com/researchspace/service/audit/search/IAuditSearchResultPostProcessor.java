package com.researchspace.service.audit.search;

import com.researchspace.core.util.ISearchResults;

/** Strategy interface for post-processing of audit search results */
public interface IAuditSearchResultPostProcessor {

  /**
   * Processes the supplied searchResult in place.
   *
   * @param searchResult
   * @return the same object.
   */
  void process(ISearchResults<AuditTrailSearchResult> searchResult);
}
