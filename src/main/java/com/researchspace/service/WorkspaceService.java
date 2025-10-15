package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.views.ServiceOperationResult;
import java.util.List;

public interface WorkspaceService {

  List<ServiceOperationResult<? extends BaseRecord>> moveRecords(
      List<Long> toMove,
      String targetFolderId,
      Long workspaceParentId,
      Long grandparentId,
      User user);
}
