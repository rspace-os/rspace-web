package com.researchspace.api.v1.model;

import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MoveRequest {
  @NotNull private long docId;

  @NotNull private long sourceFolderId;

  @NotNull private long targetFolderId;

  private Long currentGrandparentId;
}
