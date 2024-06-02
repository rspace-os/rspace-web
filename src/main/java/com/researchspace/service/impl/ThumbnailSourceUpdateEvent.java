package com.researchspace.service.impl;

import com.researchspace.model.Thumbnail.SourceType;
import org.springframework.context.ApplicationEvent;

public class ThumbnailSourceUpdateEvent extends ApplicationEvent {

  private static final long serialVersionUID = 1L;

  private SourceType sourceType;
  private Long sourceId;
  private Long sourceParentId;

  public ThumbnailSourceUpdateEvent(Object source, SourceType sourceType, Long sourceId) {
    this(source, sourceType, sourceId, null);
  }

  public ThumbnailSourceUpdateEvent(
      Object source, SourceType sourceType, Long sourceId, Long sourceParentId) {
    super(source);

    this.sourceType = sourceType;
    this.sourceId = sourceId;
    this.sourceParentId = sourceParentId;
  }

  public SourceType getSourceType() {
    return sourceType;
  }

  public Long getSourceId() {
    return sourceId;
  }

  public Long getSourceParentId() {
    return sourceParentId;
  }
}
