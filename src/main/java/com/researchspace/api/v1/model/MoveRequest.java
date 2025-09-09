package com.researchspace.api.v1.model;

import java.util.List;
import javax.validation.constraints.NotNull;

/** Request body for moving documents/records. */
public class MoveRequest {

  @NotNull private List<Long> ids;

  /** Target folder id as string; may be "/" to indicate user's root folder. */
  @NotNull private String target;

  /**
   * Optional workspace parent folder context (helps disambiguate parent when coming from listings).
   */
  private Long parentFolderId;

  public List<Long> getIds() {
    return ids;
  }

  public void setIds(List<Long> ids) {
    this.ids = ids;
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(String target) {
    this.target = target;
  }

  public Long getParentFolderId() {
    return parentFolderId;
  }

  public void setParentFolderId(Long parentFolderId) {
    this.parentFolderId = parentFolderId;
  }
}
