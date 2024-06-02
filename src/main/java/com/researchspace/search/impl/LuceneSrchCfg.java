package com.researchspace.search.impl;

import com.axiope.search.FieldNames;
import com.axiope.search.InventorySearchConfig;
import com.axiope.search.InventorySearchConfig.InventorySearchType;
import com.axiope.search.SearchConfig;
import com.axiope.search.SearchConstants;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.SearchDepth;
import com.researchspace.model.User;
import com.researchspace.model.dtos.SearchOperator;
import com.researchspace.model.dtos.WorkspaceFilters;
import com.researchspace.model.record.BaseRecord;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;

/** Configuration object for Lucene-based search */
public class LuceneSrchCfg {

  private LuceneSearchTermListFactory termListFactory;

  private Map<String, List<Term>> termList = new HashMap<>();

  private SearchConfig delegate;

  /**
   * Set representing in which records the search should be performed. (Included documents + folders
   * to search in recursively). If the set is empty, then filtering shouldn't be done.
   */
  @Getter private Set<Long> selectedRecordIds = new HashSet<>();

  @Getter private InventorySearchType searchType = InventorySearchType.ALL;

  @Getter private Long parentId;

  @Getter private Long parentTemplateId;

  @Getter private Long parentSampleId;

  /**
   * Constructor.
   *
   * @param searchConfig implementation independent searchConfig to copy data form
   * @param termListFactory required because LuceneSrchCfg isn't managed by Spring so can't autowire
   *     it
   */
  public LuceneSrchCfg(SearchConfig searchConfig, LuceneSearchTermListFactory termListFactory) {
    this.delegate = searchConfig;
    this.termListFactory = termListFactory;
    parseOptionsAndTerms(searchConfig.getOptions(), searchConfig.getTerms());

    if (searchConfig instanceof InventorySearchConfig) {
      searchType = ((InventorySearchConfig) searchConfig).getSearchType();
    }
  }

  public int getMaxResults() {
    return delegate.getMaxResults();
  }

  public int getSearchStrategy() {
    return delegate.getSearchStrategy();
  }

  public int getPageNumber() {
    return delegate.getPageNumber();
  }

  public int getPageSize() {
    return delegate.getPageSize();
  }

  public PaginationCriteria<?> getPaginationCriteria() {
    return delegate.getPaginationCriteria();
  }

  public Long getFolderId() {
    return delegate.getFolderId();
  }

  public SearchDepth getSrchDepth() {
    return delegate.getSrchDepth();
  }

  public User getAuthenticatedUser() {
    return delegate.getAuthenticatedUser();
  }

  public Map<String, List<Term>> getTermList() {
    return termList;
  }

  public List<Term> getAllTerms() {
    return termList.values().stream().flatMap(List::stream).collect(Collectors.toList());
  }

  public Set<String> getTermListFields() {
    return termList.keySet();
  }

  public boolean isAdvancedSearch() {
    return delegate.isAdvancedSearch();
  }

  public SearchOperator getOperator() {
    return delegate.getOperator();
  }

  public boolean isNotebookFilter() {
    return delegate.isNotebookFilter();
  }

  public List<String> getUsernameFilterList() {
    return delegate.getUsernameFilter();
  }

  public List<String> getSharedWithFilterList() {
    return delegate.getSharedWithFilter();
  }

  public boolean isRestrictByUser() {
    return delegate.isRestrictByUser();
  }

  public List<BaseRecord> getRecordFilterList() {
    return delegate.getRecordFilterList();
  }

  /**
   * Boolean test for whether record filter list is usable directly in Lucene. <br>
   * This is the case if it is non-empty but smaller than <code>BooleanQuery.getMaxClauseCount()
   * </code> currently 1024 ( see RSPAC-1825)
   */
  public boolean isRecordFilterListUsableInLucene() {
    return !CollectionUtils.isEmpty(getRecordFilterList())
        && getRecordFilterList().size() <= BooleanQuery.getMaxClauseCount();
  }

  public WorkspaceFilters getFilters() {
    return delegate.getFilters();
  }

  /**
   * Function to parse options and terms after being set. Extracts both terms for lucene search, as
   * well as other things like records filter. Should be called by every constructor and setter for
   * search options and terms
   */
  private void parseOptionsAndTerms(String[] options, String[] terms) {
    // Get Lucene terms
    termList = termListFactory.getTermList(getAuthenticatedUser(), options, terms);

    // Extract additional info
    for (int i = 0; i < options.length; ++i) {
      String currOption = options[i];
      String currTerm = terms[i];
      if (currOption.equals(SearchConstants.RECORDS_SEARCH_OPTION)) {
        Arrays.stream(currTerm.split("\\s*[,;]\\s*"))
            .map(recordId -> recordId.substring(2)) // Get rid of record type prefix
            .map(Long::parseLong)
            .forEach(selectedRecordIds::add);
      } else if (currOption.equals(SearchConstants.INVENTORY_PARENT_ID_OPTION)) {
        parentId = Long.parseLong(currTerm);
      } else if (currOption.equals(SearchConstants.INVENTORY_PARENT_TEMPLATE_ID_OPTION)) {
        parentTemplateId = Long.parseLong(currTerm);
      } else if (currOption.equals(SearchConstants.INVENTORY_PARENT_SAMPLE_ID_OPTION)) {
        parentSampleId = Long.parseLong(currTerm);
      }
    }
  }

  /**
   * @return value of the first Field_Data term
   */
  public Optional<String> getFullTextOption() {
    if (!termList.containsKey(FieldNames.FIELD_DATA)) return Optional.empty();
    return Optional.of(termList.get(FieldNames.FIELD_DATA).get(0).text());
  }

  /**
   * Whether we should filter out results that are not included in the record selection. This will
   * be true if user has restricted search to 1 or more records/folders/notebooks in 'advanced
   * search' using 'records' option
   *
   * @return true if at least 1 folder or document is selected
   */
  public boolean areRecordsSelected() {
    return !selectedRecordIds.isEmpty();
  }
}
