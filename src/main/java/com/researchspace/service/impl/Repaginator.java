package com.researchspace.service.impl;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.model.PaginationCriteria;
import java.util.List;

class Repaginator {

  /**
   * Utility method to paginate the results using the pagination criteria values.
   *
   * @param paginationCriteria
   * @param rdsx
   * @param totalHits
   * @return
   */
  static <K> ISearchResults<K> paginateResults(
      PaginationCriteria<K> paginationCriteria, List<K> rdsx, int totalHits) {

    int pageSize = paginationCriteria.getResultsPerPage();
    int start = paginationCriteria.getPageNumber().intValue() * pageSize;
    int end = totalHits - start >= pageSize ? start + pageSize : start + (totalHits % pageSize);
    if (end > totalHits) { // we asked for a page too far
      return SearchResultsImpl.emptyResult(paginationCriteria);
    }
    List<K> sub = rdsx.subList(start, end);
    return new SearchResultsImpl<>(sub, paginationCriteria, totalHits);
  }
}
