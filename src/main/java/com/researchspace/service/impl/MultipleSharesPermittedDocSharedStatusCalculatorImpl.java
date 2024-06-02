package com.researchspace.service.impl;

import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.model.AbstractUserOrGroupImpl;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.DocumentSharedStateCalculator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Default strategy returns <code>true</code> (i.e., sharing can proceed) if the document is not
 * already shared with the exact same user or group.
 */
public class MultipleSharesPermittedDocSharedStatusCalculatorImpl
    implements DocumentSharedStateCalculator {

  public void setGroupshareRecordDao(RecordGroupSharingDao groupshareRecordDao) {
    this.groupshareRecordDao = groupshareRecordDao;
  }

  @Autowired private RecordGroupSharingDao groupshareRecordDao;

  @Override
  public boolean canShare(AbstractUserOrGroupImpl userOrGroup, BaseRecord document, User subject) {
    // this is a fast lookup
    List<AbstractUserOrGroupImpl> sharees =
        groupshareRecordDao.getUsersOrGroupsWithRecordAccess(document.getId());
    // if we checking a user, see if existing groups contain user
    boolean isAlreadyShared = false;
    // simple case, it's already shared with this group or user
    if (sharees.contains(userOrGroup)) {
      isAlreadyShared = true;
    }
    return !isAlreadyShared;
  }
}
