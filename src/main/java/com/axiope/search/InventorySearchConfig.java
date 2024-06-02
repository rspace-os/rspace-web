package com.axiope.search;

import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.dtos.WorkspaceFilters;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.record.BaseRecord;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Configuration object for RSpace Inventory Search. */
@Getter
@Setter
public class InventorySearchConfig extends SearchConfig {

  public static enum InventorySearchType {
    ALL,
    SAMPLE,
    SUBSAMPLE,
    CONTAINER,
    TEMPLATE
  }

  /** Strategy for including deleted items in inventory search results */
  public static enum InventorySearchDeletedOption {
    EXCLUDE,
    INCLUDE,
    DELETED_ONLY
  }

  private InventorySearchType searchType = InventorySearchType.ALL;

  private InventorySearchDeletedOption deletedOption = InventorySearchDeletedOption.EXCLUDE;

  private List<String> limitResultsToGlobalIds;

  private String defaultTemplatesOwner;

  private GlobalIdentifier parentOid;

  private String originalSearchQuery;

  /**
   * Constructor using default settings for all other properties. Constructor using settings for
   * advanced search.
   *
   * @param authenticatedUser
   */
  public InventorySearchConfig(final User authenticatedUser) {
    super(authenticatedUser);
    paginationCriteria = PaginationCriteria.createDefaultForClass(InventoryRecord.class);
  }

  /*
   * For testing setup
   */
  InventorySearchConfig() {}

  /*
   * unsupported IFullTextSearchConfig methods below
   */
  @Override
  public Long getFolderId() {
    return null;
  }

  @Override
  public void setFolderId(Long folderId) {
    throw new UnsupportedOperationException("option not supported for the inventory search");
  }

  @Override
  public boolean isNotebookFilter() {
    return false;
  }

  @Override
  public void setNotebookFilter(boolean notebookFilter) {
    throw new UnsupportedOperationException("option not supported for the inventory search");
  }

  @Override
  public List<BaseRecord> getRecordFilterList() {
    return null;
  }

  @Override
  public void setRecordFilterList(List<BaseRecord> recordFilterList) {
    throw new UnsupportedOperationException("option not supported for the inventory search");
  }

  @Override
  public WorkspaceFilters getFilters() {
    return null;
  }

  @Override
  public void setFilters(WorkspaceFilters filters) {
    throw new UnsupportedOperationException("option not supported for the inventory search");
  }

  @Override
  public boolean isReturnAttachmentsOnly() {
    return false;
  }

  @Override
  public void setReturnAttachmentsOnly(boolean simpleAttachmentSearch) {
    throw new UnsupportedOperationException("option not supported for the inventory search");
  }
}
