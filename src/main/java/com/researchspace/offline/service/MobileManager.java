package com.researchspace.offline.service;

import com.researchspace.offline.model.OfflineRecord;
import com.researchspace.offline.model.OfflineRecordInfo;
import java.io.IOException;
import java.util.List;

public interface MobileManager {

  List<OfflineRecordInfo> getOfflineRecordList(String username);

  OfflineRecord getRecord(Long recordId, String username) throws Exception;

  Long uploadRecord(OfflineRecord record, String username) throws IOException;
}
