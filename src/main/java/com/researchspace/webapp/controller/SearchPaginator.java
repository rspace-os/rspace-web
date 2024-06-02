package com.researchspace.webapp.controller;

import com.axiope.search.IFullTextSearchConfig;
import com.axiope.search.WorkspaceSearchConfig;
import com.researchspace.core.util.AbstractURLPaginator;
import com.researchspace.model.User;
import com.researchspace.model.dtos.SearchOperator;
import com.researchspace.model.dtos.WorkspaceListingConfig;

public class SearchPaginator extends AbstractURLPaginator {

  private IFullTextSearchConfig searchCfg;

  public SearchPaginator(WorkspaceListingConfig input, User user) {
    super(input.getPgCrit());
    initCfgObject(
        user,
        input.isAdvancedSearch(),
        input.getParentFolderId(),
        input.getSrchOptions(),
        input.getSrchTerms(),
        input.getOperator());
  }

  private void initCfgObject(
      User user,
      boolean isAdvanced,
      Long folderId,
      String[] options,
      String[] terms,
      SearchOperator operator) {
    this.searchCfg = new WorkspaceSearchConfig(user);
    searchCfg.setAdvancedSearch(isAdvanced);
    searchCfg.setFolderId(folderId);
    searchCfg.setOptions(options);
    searchCfg.setTerms(terms);
    searchCfg.setOperator(operator);
  }

  @Override
  public String generateURL(int pageNum) {

    String url =
        "workspace/ajax/search?"
            + "isAdvancedSearch="
            + searchCfg.isAdvancedSearch()
            + "&recordId="
            + searchCfg.getFolderId()
            + "&options[]="
            + searchCfg.getOptionsURL()
            + "&terms[]="
            + searchCfg.getTermsURL()
            + "&pageNumber="
            + pageNum
            // paginationCriteria property
            + "&resultsPerPage="
            + pgCrit.getResultsPerPage()
            + "&operator="
            + searchCfg.getOperator();

    if (pgCrit.getOrderBy() != null) {
      String orderBy = "&orderBy=" + pgCrit.getOrderBy();
      url = url.concat(orderBy + "&sortOrder=" + pgCrit.getSortOrder());
    }

    return url;
  }

  @Override
  public String generateURLForCurrentPage(int pageNum) {
    return "#";
  }
}
