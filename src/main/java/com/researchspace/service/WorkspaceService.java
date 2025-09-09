package com.researchspace.service;

import com.researchspace.model.User;

/** Service for workspace-related operations shared between web MVC and REST API layers. */
public interface WorkspaceService {

  /**
   * Move one or more records (documents or folders) to a target folder.
   *
   * @param toMove ids of records to move
   * @param targetFolderId string representation of target folder id; may be "/" for root
   * @param workspaceParentId optional workspace parent folder context (may be null)
   * @param user acting user
   * @return number of items successfully moved
   */
  int moveRecords(Long[] toMove, String targetFolderId, Long workspaceParentId, User user);
}
