package com.researchspace.offline.service.impl;

import com.researchspace.model.EditStatus;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecord.OfflineWorkStatus;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.offline.dao.OfflineRecordUserDao;
import com.researchspace.offline.model.OfflineRecordUser;
import com.researchspace.offline.model.OfflineWorkType;
import com.researchspace.offline.service.OfflineManager;
import com.researchspace.service.RecordManager;
import com.researchspace.session.UserSessionTracker;
import java.util.List;
import org.apache.commons.lang.Validate;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("offlineManager")
public class OfflineManagerImpl implements OfflineManager {

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Autowired private OfflineRecordUserDao offlineRecordUserDao;

  @Autowired private RecordManager recordManager;

  @Autowired private IPermissionUtils permissionUtils;

  @Override
  public OfflineRecordUser getOfflineWork(Long recordId, Long userId) {
    return offlineRecordUserDao.getOfflineWork(recordId, userId);
  }

  @Override
  public List<OfflineRecordUser> getOfflineWorkForUser(User user) {
    return offlineRecordUserDao.getOfflineWorkForUser(user);
  }

  @Override
  public OfflineRecordUser addRecordForOfflineWork(
      BaseRecord record, User user, UserSessionTracker activeUsers) {
    Validate.notNull(record, "record can't be null");
    Validate.notNull(user, "user can't be null");

    if (!canBeMarkedForOffline(record)) {
      throw new UnsupportedOperationException(
          "only Basic Documents can be marked for offline work");
    }

    boolean accessPermitted = permissionUtils.isPermitted(record, PermissionType.READ, user);
    if (!accessPermitted) {
      throw new AuthorizationException(
          "user " + user.getId() + " can't read record " + record.getId());
    }

    EditStatus recordEditStatus = getRecordEditStatus((Record) record, user, activeUsers);
    if (EditStatus.CANNOT_EDIT_OTHER_EDITING.equals(recordEditStatus)) {
      throw new IllegalStateException(
          record.getName() + " is currently edited, please select another record");
    }

    // remove existing record lock in case user has one
    removeRecordFromOfflineWork(record.getId(), user);

    boolean canUserEdit = EditStatus.EDIT_MODE.equals(recordEditStatus);
    OfflineWorkType permittedWorkType = canUserEdit ? OfflineWorkType.EDIT : OfflineWorkType.VIEW;

    return offlineRecordUserDao.createOfflineWork(record, user, permittedWorkType);
  }

  private EditStatus getRecordEditStatus(Record record, User user, UserSessionTracker activeUsers) {
    EditStatus recordEditStatus =
        recordManager.requestRecordEdit(record.getId(), user, activeUsers);
    // we don't want to hold the lock
    recordManager.unlockRecord(record, user);
    return recordEditStatus;
  }

  @Override
  public void removeRecordFromOfflineWork(Long recordId, User user) {
    offlineRecordUserDao.removeOfflineWork(recordId, user);
  }

  @Override
  public void loadOfflineWorkStatusOfRecords(List<BaseRecord> records, User user) {
    if (records == null) {
      return;
    }
    for (BaseRecord record : records) {
      if (!canBeMarkedForOffline(record)) {
        record.setOfflineWorkStatus(OfflineWorkStatus.NOT_APPLICABLE);
        continue;
      }

      List<OfflineRecordUser> recordUsage = offlineRecordUserDao.getOfflineWorkForRecord(record);
      OfflineWorkStatus workStatus = getOfflineWorkStatusForUsage(recordUsage, user);
      record.setOfflineWorkStatus(workStatus);
    }
  }

  // we only allow Basic Documents right now
  private boolean canBeMarkedForOffline(BaseRecord record) {
    if (record instanceof StructuredDocument) {
      return ((StructuredDocument) record).isBasicDocument();
    }
    return false;
  }

  private OfflineWorkStatus getOfflineWorkStatusForUsage(
      List<OfflineRecordUser> offlineUsage, User user) {
    if (offlineUsage == null || offlineUsage.isEmpty()) {
      return OfflineWorkStatus.NOT_OFFLINE;
    }

    OfflineWorkStatus result = OfflineWorkStatus.OTHER_VIEW;
    for (OfflineRecordUser recordUserLock : offlineUsage) {
      boolean currUser = recordUserLock.getUser().equals(user);
      boolean editLock = OfflineWorkType.EDIT.equals(recordUserLock.getWorkType());

      if (currUser && editLock) {
        result = OfflineWorkStatus.USER_EDIT;
      } else if (currUser) {
        result = OfflineWorkStatus.USER_VIEW;
      } else if (editLock) {
        // other_edit has lower priority than user view
        if (result == OfflineWorkStatus.OTHER_VIEW) {
          result = OfflineWorkStatus.OTHER_EDIT;
        }
      }
    }
    return result;
  }
}
