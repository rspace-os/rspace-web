package com.researchspace.search.impl;

import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Applies some filters to search hits from Lucene query, before final pagination.
 *
 * <p><em>Note</em> This class is stateful, not thread-safe and should be instantiated for each new
 * search.
 */
public class PostTextSearchResultFilterer {

  private List<BaseRecord> initialHits;
  private LuceneSrchCfg luceneSrchConfig;

  private Set<Long> selectedRecords;

  public PostTextSearchResultFilterer(List<BaseRecord> initialHits, LuceneSrchCfg config) {
    this.initialHits = initialHits;
    luceneSrchConfig = config;
    // Making a copy so that we can mutate it safely
    selectedRecords = new HashSet<>(config.getSelectedRecordIds());
  }

  // applies filters based on configuration in the LuceneSrchCfg supplied to this object
  public List<BaseRecord> filterAll() {
    Stream<BaseRecord> filterStream =
        initialHits.stream()
            .filter(this::filterInvisibleRecords)
            .filter(this::filterDeletedRecords);
    if (luceneSrchConfig.isNotebookFilter())
      filterStream = filterStream.filter(this::filterResultsByNotebook);
    if (luceneSrchConfig.areRecordsSelected())
      filterStream = filterStream.filter(this::filterUnselected);
    // only apply this filter post-search if it couldn't be applied in Lucene query.
    // this is a filter related to shared records when searching in group folder
    if (luceneSrchConfig.getRecordFilterList() != null
        && !luceneSrchConfig.isRecordFilterListUsableInLucene()) {
      filterStream = filterStream.filter(this::filterByRecordFilter);
    }

    return filterStream.collect(Collectors.toList());
  }

  /*
   * Removing invisible records from the result.
   *
   * @param baseRecordList
   */
  private boolean filterInvisibleRecords(BaseRecord toFilter) {
    return !toFilter.isInvisible();
  }

  /*
   * Removing deleted records from the result.
   *
   * @param baseRecordList
   * @param srchConfig
   */
  private boolean filterDeletedRecords(BaseRecord toFilter) {
    return !(toFilter.isDeleted()
        || toFilter.isDeletedForUser(luceneSrchConfig.getAuthenticatedUser()));
  }

  private boolean filterByRecordFilter(BaseRecord hit) {
    return luceneSrchConfig.getRecordFilterList().contains(hit);
  }

  /*
   * This method iterates through every element in results list and
   * checks if it belongs to the notebook.
   *
   * @param baseRecordList
   * @return
   */
  private boolean filterResultsByNotebook(BaseRecord hit) {
    Folder notebook = hit.getParent();
    return notebook != null && notebook.getId().equals(luceneSrchConfig.getFolderId());
  }

  /**
   * Removes records that are not included in the folder and document selection. Note that this
   * should only be called if one or more records are selected, otherwise it will return false for
   * all records!!!
   *
   * @return true if the record is included in the selection.
   */
  private boolean filterUnselected(BaseRecord hit) {
    return isRecordSelected(hit);
  }

  /**
   * Recursive function to check if folder or one of its ancestors is included in the folder
   * selection
   *
   * <p>Works in O(k) time, where k is the max folder depth in the user's file hierarchy. In the
   * future, for additional speedup, can consider making a hash-set for non-selected records as
   * well, to speed up the function for queries that will return false.
   *
   * @param record to check if it or its children should appear in the search results
   * @return true if the folder or one of its ancestors is selected
   */
  private boolean isRecordSelected(BaseRecord record) {
    if (selectedRecords.contains(record.getId())) return true;
    else if (record.isFolder() && ((Folder) record).isRootFolder()) return false;
    else {
      for (Folder parent : record.getParentFolders()) {
        if (isRecordSelected(parent)) {
          selectedRecords.add(parent.getId()); // To speed up later searches
          return true;
        }
      }
      return false;
    }
  }
}
