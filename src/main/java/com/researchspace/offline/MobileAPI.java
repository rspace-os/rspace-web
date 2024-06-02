package com.researchspace.offline;

import com.researchspace.offline.model.OfflineRecord;
import com.researchspace.offline.model.OfflineRecordInfo;
import java.security.Principal;
import java.util.List;

public interface MobileAPI {

  /**
   * should return the list of recordIds that were marked for offline work, with their lock status
   * (view or edit) and last modification date
   */
  List<OfflineRecordInfo> getOfflineRecordList(Principal p) throws Exception;

  /** download single record for view or edit */
  OfflineRecord downloadRecord(Long recordId, Principal p) throws Exception;

  /** upload single record, either edited or new one */
  Long uploadRecord(OfflineRecord record, Principal p) throws Exception;
}
