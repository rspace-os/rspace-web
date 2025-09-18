package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.views.ServiceOperationResult;
import java.util.List;

public interface WorkspaceService {

  /**
   * Move one or more records (documents or folders) to a target folder.
   *
   * @param toMove ids of records to move
   * @param targetFolderId string representation of target folder id; may be "/" for root
   * @param workspaceParentId optional workspace parent folder context (may be null)
   * @param user acting user
   * @return List of the results of each move attempt
   */
  List<ServiceOperationResult<? extends BaseRecord>> moveRecords(
      List<Long> toMove, String targetFolderId, Long workspaceParentId, User user);

  /*
  As above, but counts the number of successful moves, to maintain compatibility with the original methods in WorkspaceController.
   */
  int moveRecordsCountSuccess(
      List<Long> toMove, String targetFolderId, Long workspaceParentId, User user);
}
