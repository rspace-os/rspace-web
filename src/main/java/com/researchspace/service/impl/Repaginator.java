package com.researchspace.service.impl;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.record.BaseRecord;
import java.util.List;

class Repaginator {

  /**
   * Utility method to re paginate the results using the pagination criteria values.
   *
   * @param paginationCriteria
   * @param rdsx
   * @param totalHits
   * @return
   */
  static ISearchResults<BaseRecord> repaginateResults(
      PaginationCriteria<BaseRecord> paginationCriteria,
      ISearchResults<BaseRecord> rdsx,
      int totalHits) {
    int pageSize = paginationCriteria.getResultsPerPage();
    int start = paginationCriteria.getPageNumber().intValue() * pageSize;
    int end = totalHits - start >= pageSize ? start + pageSize : start + (totalHits % pageSize);
    if (end > totalHits) { // we asked for a page too far
      return SearchResultsImpl.emptyResult(paginationCriteria);
    }
    List<BaseRecord> sub = rdsx.getResults().subList(start, end);
    return new SearchResultsImpl<BaseRecord>(sub, paginationCriteria, totalHits);
  }
}
