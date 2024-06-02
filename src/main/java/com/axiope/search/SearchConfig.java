package com.axiope.search;

import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.SearchDepth;
import com.researchspace.model.User;
import com.researchspace.model.dtos.SearchOperator;
import com.researchspace.model.record.BaseRecord;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;

/** Configuration object for Search, independent of implementation. */
@Getter
@Setter
public abstract class SearchConfig implements IFullTextSearchConfig {

  private boolean isAdvancedSearch;
  private int searchStrategy = IFullTextSearcher.DEFAULT_LUCENE_SEARCH_STRATEGY;
  private int pageNumber = 0;
  private int pageSize = PAGE_SIZE;
  private SearchDepth srchDepth = SearchDepth.INFINITE;

  private User authenticatedUser;

  protected PaginationCriteria<?> paginationCriteria =
      PaginationCriteria.createDefaultForClass(BaseRecord.class);
  private SearchOperator operator = SearchOperator.AND;
  private List<String> usernameFilter = new ArrayList<>();
  private List<String> sharedWithFilter = new ArrayList<>();
  private boolean restrictByUser = true;

  // default can be set for testing
  private int maxResults = 1000;
  private String[] options = new String[0];
  private String[] terms = new String[0];

  /**
   * Constructor using default settings for all other properties. Constructor using settings for
   * advanced search.
   *
   * @param authenticatedUser
   */
  public SearchConfig(final User authenticatedUser) {
    this.authenticatedUser = authenticatedUser;
  }

  /*
   * For testing setup
   */
  SearchConfig() {}

  /* (non-Javadoc)
   * @see com.axiope.search.IFullTextSearchConfig#setPageNumber(int)
   */
  @Override
  public void setPageNumber(int pageNumber) {
    if (pageNumber < 0) {
      throw new IllegalArgumentException("Page number must be > 0 but was [" + pageNumber + "]");
    }
    this.pageNumber = pageNumber;
  }

  /* (non-Javadoc)
   * @see com.axiope.search.IFullTextSearchConfig#setPaginationCriteria(com.researchspace.model.PaginationCriteria)
   */
  @Override
  public void setPaginationCriteria(PaginationCriteria<?> pg) {
    if (pg == null) {
      throw new IllegalArgumentException("paginationCriteria cannot be null!");
    }
    this.paginationCriteria = pg;
  }

  /* (non-Javadoc)
   * @see com.axiope.search.IFullTextSearchConfig#setPageSize(int)
   */
  @Override
  public void setPageSize(int pageSize) {
    if (pageSize < 0) {
      throw new IllegalArgumentException("Page size must be > 0 but was [" + pageSize + "]");
    }
    this.pageSize = pageSize;
  }

  @Override
  public String getOptionsURL() {
    return StringUtils.join(options, ",");
  }

  @Override
  public String getTermsURL() {
    return StringUtils.join(terms, ",");
  }
}
