package com.researchspace.service.impl;

import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.PreWorkspaceViewRecordStatusManager;
import com.researchspace.service.RecordFavoritesManager;
import com.researchspace.service.RecordSharingManager;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Retrieve additional information to set into {@link BaseRecord}s returned to workspace. These will
 * operate in a single transaction rather than multiple individual ones.
 */
@Service
public class PreWorkspaceViewRecordStatusManagerImpl
    implements PreWorkspaceViewRecordStatusManager {

  private @Autowired RecordSharingManager sharingManager;
  private @Autowired RecordFavoritesManager favoritesManager;

  @Override
  public void setStatuses(Collection<BaseRecord> records, User subject) {
    sharingManager.updateSharedStatusOfRecords(records, subject);
    favoritesManager.updateTransientFavoriteStatus(records, subject);
  }
}
