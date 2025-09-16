package com.researchspace.api.v1.model;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class MoveRequest {
  @NotNull
  private long docId;

  @NotNull
  private long sourceFolderId;

  @NotNull
  private long targetFolderId;
}
