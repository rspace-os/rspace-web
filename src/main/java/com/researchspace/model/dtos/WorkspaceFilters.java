package com.researchspace.model.dtos;

import com.researchspace.model.audittrail.AuditTrailData;
import java.io.Serializable;
import lombok.Data;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component()
@Scope("request")
@AuditTrailData
@Data
public class WorkspaceFilters implements Serializable {

  private static final long serialVersionUID = -1007865220823972625L;

  private boolean sharedFilter = false;
  private boolean favoritesFilter = false;
  private boolean viewableItemsFilter = false;
  private boolean templatesFilter = false;
  private boolean ontologiesFilter = false;

  private boolean documentsFilter = false;
  private boolean mediaFilesFilter = false;
  private String mediaFilesType;

  public WorkspaceFilters() {}

  /**
   * Boolean test as to whether any filter is active.
   *
   * @return
   */
  public boolean isSomeFilterActive() {
    return isSharedFilter()
        || isFavoritesFilter()
        || isViewableItemsFilter()
        || isMediaFilesFilter()
        || isTemplatesFilter()
        || isOntologiesFilter();
  }
}
