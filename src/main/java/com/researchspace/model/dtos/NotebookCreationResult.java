package com.researchspace.model.dtos;

import lombok.Value;

@Value
public class NotebookCreationResult {
  long notebookId;
  long grandParentId;
  String groupName;
}
