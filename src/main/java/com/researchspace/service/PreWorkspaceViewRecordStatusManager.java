package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import java.util.Collection;

/** Responsible for querying statuses and flags for a result set */
public interface PreWorkspaceViewRecordStatusManager {
  /**
   * Updates information in {@link BaseRecord} for presentation in UI.
   *
   * @param records
   * @param subject
   */
  void setStatuses(Collection<BaseRecord> records, User subject);
}
