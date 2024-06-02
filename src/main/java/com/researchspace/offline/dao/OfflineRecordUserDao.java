package com.researchspace.offline.dao;

import com.researchspace.dao.GenericDao;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.offline.model.OfflineRecordUser;
import com.researchspace.offline.model.OfflineWorkType;
import java.util.List;

public interface OfflineRecordUserDao extends GenericDao<OfflineRecordUser, Long> {

  OfflineRecordUser getOfflineWork(Long recordId, Long userId);

  OfflineRecordUser createOfflineWork(BaseRecord record, User user, OfflineWorkType workType);

  void removeOfflineWork(Long recordId, User user);

  List<OfflineRecordUser> getOfflineWorkForRecord(BaseRecord record);

  List<OfflineRecordUser> getOfflineWorkForUser(User user);
}
