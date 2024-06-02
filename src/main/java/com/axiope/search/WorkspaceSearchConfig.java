package com.axiope.search;

import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.WorkspaceFilters;
import com.researchspace.model.record.BaseRecord;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Configuration object for Search, independent of implementation. */
@Getter
@Setter
public class WorkspaceSearchConfig extends SearchConfig {

  private Long folderId = -1L;
  private boolean notebookFilter;
  private boolean returnAttachmentsOnly;
  private WorkspaceFilters filters = new WorkspaceFilters();
  private List<BaseRecord> recordFilterList;

  /**
   * Constructor using default settings for all other properties. Constructor using settings for
   * advanced search.
   *
   * @param authenticatedUser
   */
  public WorkspaceSearchConfig(final User authenticatedUser) {
    super(authenticatedUser);
    paginationCriteria = PaginationCriteria.createDefaultForClass(BaseRecord.class);
  }

  /*
   * For testing setup
   */
  WorkspaceSearchConfig() {}
}
