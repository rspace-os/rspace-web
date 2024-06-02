package com.researchspace.service;

import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.dtos.ShareConfigCommand;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.model.views.ServiceOperationResultCollection;

/** Facade to share documents/notebooks */
public interface SharingHandler {

  ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> shareRecords(
      ShareConfigCommand shareConfig, User subject);

  ServiceOperationResult<RecordGroupSharing> unshare(Long recordGroupShareId, User subject);

  /**
   * Bulk unshare of all documents that were shared with a group
   *
   * @param subject
   * @param group
   * @param notify whether a notification should be sent per document unshared (<code>true</code>)
   *     or should be silent (<code>false</code>)
   * @return
   */
  ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> unshareAllWithGroup(
      User subject, Group group, boolean notify);
}
