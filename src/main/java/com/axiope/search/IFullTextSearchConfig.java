package com.axiope.search;

import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.SearchDepth;
import com.researchspace.model.User;
import com.researchspace.model.dtos.SearchOperator;
import com.researchspace.model.dtos.WorkspaceFilters;
import com.researchspace.model.record.BaseRecord;
import java.util.List;

public interface IFullTextSearchConfig {

  /** */
  int PAGE_SIZE = 10;

  /** Smaller restraint on Sysadmin searches as these are likely to lead to more results */
  int MAX_SYSADMIN_RESULTS = 500;

  /**
   * An absolute limit on the number of results to retrieve <em>before</em> filtering by permission
   * etc
   *
   * @return
   */
  int getMaxResults();

  /*
   * Probably just useful for testing, we don't want end-user to configure this.
   */
  void setMaxResults(int maxResults);

  int getSearchStrategy();

  void setSearchStrategy(int searchStrategy);

  int getPageNumber();

  void setPageNumber(int pageNumber);

  int getPageSize();

  PaginationCriteria<?> getPaginationCriteria();

  void setPaginationCriteria(PaginationCriteria<?> pg);

  void setPageSize(int pageSize);

  Long getFolderId();

  void setFolderId(Long folderId);

  /**
   * the SearchDepth strategy to use -
   *
   * <ul>
   *   <li>INFINITE ( to search all descendant folders)
   *   <li>or ZERO ( to search only direct children of the parentFolder)
   *   <li>or GLOBAL if wanting to include all records.
   * </ul>
   */
  SearchDepth getSrchDepth();

  void setSrchDepth(SearchDepth srchDepth);

  User getAuthenticatedUser();

  void setAuthenticatedUser(User authenticatedUser);

  /**
   * @return string of concatenated options representing the options part in the url used for
   *     searching. Is needed to generate paginated links.
   */
  String getOptionsURL();

  /**
   * @return string of concatenated terms representing the terms part in the url used for searching.
   *     Is needed to generate paginated links.
   */
  String getTermsURL();

  void setOptions(String[] options);

  void setTerms(String[] terms);

  boolean isAdvancedSearch();

  void setAdvancedSearch(boolean isAdvancedSearch);

  SearchOperator getOperator();

  void setOperator(SearchOperator operator);

  boolean isNotebookFilter();

  void setNotebookFilter(boolean notebookFilter);

  /** A List of usernames whose documents we are restricting the search to. */
  void setUsernameFilter(List<String> userFilter);

  /**
   * @return list of usernames the subject user has permission to view / search files of.
   */
  List<String> getUsernameFilter();

  List<BaseRecord> getRecordFilterList();

  void setRecordFilterList(List<BaseRecord> recordFilterList);

  WorkspaceFilters getFilters();

  void setFilters(WorkspaceFilters filters);

  boolean isReturnAttachmentsOnly();

  void setReturnAttachmentsOnly(boolean simpleAttachmentSearch);
}
