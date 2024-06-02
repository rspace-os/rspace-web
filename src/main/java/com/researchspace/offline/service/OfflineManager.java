package com.researchspace.offline.service;

import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.offline.model.OfflineRecordUser;
import com.researchspace.session.UserSessionTracker;
import java.util.List;

public interface OfflineManager {

  OfflineRecordUser getOfflineWork(Long recordId, Long userId);

  List<OfflineRecordUser> getOfflineWorkForUser(User user);

  /**
   * @return created offline work record, or null if couldn't create
   */
  OfflineRecordUser addRecordForOfflineWork(
      BaseRecord record, User user, UserSessionTracker activeUsers);

  void removeRecordFromOfflineWork(Long recordId, User user);

  void loadOfflineWorkStatusOfRecords(List<BaseRecord> records, User user);
}
